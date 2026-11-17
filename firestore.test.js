const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write any user data", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const ts = new Date(Date.now() - 1000);
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("accounts").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("transactions").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("accounts")
      .doc("acc_1")
      .set({
        userId: ALICE_UID,
        name: "Checking",
        type: "CHECKING",
        balanceCents: 100000,
        colorHex: "#059669",
        primaryAccount: true,
        archived: false,
        createdAt: ts,
        updatedAt: ts,
      })
  );
});

test("User profile creation, update, and cross-user isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      currencySymbol: "$",
      monthlyBudgetCents: 250000,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Partial update of budget & currency succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      monthlyBudgetCents: 300000,
      currencySymbol: "€",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Ghost field rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Future timestamp rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      monthlyBudgetCents: 100000,
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Cross-user access denied
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      monthlyBudgetCents: 100,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Accounts: valid CRUD, balance updates, and @DocumentId key rejection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("accounts")
      .doc("acc_checking")
      .set({
        userId: ALICE_UID,
        name: "Main Checking",
        type: "CHECKING",
        balanceCents: 150000,
        colorHex: "#059669",
        primaryAccount: true,
        archived: false,
        createdAt: created,
        updatedAt: created,
      })
  );

  // Balance-only batch update succeeds
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("accounts")
      .doc("acc_checking")
      .update({
        balanceCents: 145000,
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  // List subcollection succeeds for owner, fails for other user
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("accounts").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("accounts").get()
  );

  // Including `id` field fails
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("accounts")
      .doc("acc_bad")
      .set({
        id: "acc_bad",
        userId: ALICE_UID,
        name: "Bad",
        type: "CASH",
        balanceCents: 0,
        colorHex: "#059669",
        primaryAccount: false,
        archived: false,
        createdAt: created,
        updatedAt: created,
      })
  );
});

test("Transactions: expense, transfer, validation bounds, and immutable createdAt", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 5000);

  // Expense (optional toAccountId/toAccountName omitted)
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("transactions")
      .doc("tx_1")
      .set({
        userId: ALICE_UID,
        type: "EXPENSE",
        amountCents: 1250,
        category: "Coffee",
        accountId: "acc_checking",
        accountName: "Main Checking",
        note: "",
        dateEpochMs: Date.now() - 2000,
        createdAt: created,
        updatedAt: created,
      })
  );

  // Transfer requires distinct toAccountId & toAccountName
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("transactions")
      .doc("tx_2")
      .set({
        userId: ALICE_UID,
        type: "TRANSFER",
        amountCents: 50000,
        category: "Transfer",
        accountId: "acc_checking",
        accountName: "Main Checking",
        toAccountId: "acc_savings",
        toAccountName: "Savings",
        note: "Monthly save",
        dateEpochMs: Date.now() - 1500,
        createdAt: created,
        updatedAt: created,
      })
  );

  // Same-account transfer fails
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("transactions")
      .doc("tx_bad_transfer")
      .set({
        userId: ALICE_UID,
        type: "TRANSFER",
        amountCents: 500,
        category: "Transfer",
        accountId: "acc_checking",
        accountName: "Main Checking",
        toAccountId: "acc_checking",
        toAccountName: "Main Checking",
        note: "",
        dateEpochMs: Date.now() - 1500,
        createdAt: created,
        updatedAt: created,
      })
  );

  // Zero/negative amount fails
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("transactions")
      .doc("tx_zero")
      .set({
        userId: ALICE_UID,
        type: "EXPENSE",
        amountCents: 0,
        category: "Coffee",
        accountId: "acc_checking",
        accountName: "Main Checking",
        note: "",
        dateEpochMs: Date.now() - 1500,
        createdAt: created,
        updatedAt: created,
      })
  );

  // Immutable createdAt cannot be modified on update
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("transactions")
      .doc("tx_1")
      .update({
        createdAt: new Date(Date.now() - 500),
        updatedAt: new Date(Date.now() - 500),
      })
  );
});

test("Category budgets: valid create, update, and cross-user denial", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("budgets")
      .doc("food")
      .set({
        userId: ALICE_UID,
        category: "Food",
        limitCents: 45000,
        createdAt: created,
        updatedAt: created,
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("budgets")
      .doc("food")
      .update({
        limitCents: 50000,
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("budgets")
      .doc("food")
      .delete()
  );
});
