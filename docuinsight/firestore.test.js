const { initializeTestEnvironment, assertFails, assertSucceeds } = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

const PROJECT_ID = "demo-no-project";
const DATABASE_ID = "ai-studio-android-docuinsi-c3693c09-e49a-496b-a731-7db7240bd597";
let testEnv;

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules: fs.readFileSync("firestore.rules", "utf8"),
      host: "127.0.0.1",
      port: 8085
    }
  });
});

beforeEach(async () => {
  await testEnv.clearFirestore();
});

after(async () => {
  await testEnv.cleanup();
});

test("Unauthenticated user cannot read documents", async () => {
  const unauthedDb = testEnv.unauthenticatedContext().firestore({ databaseId: DATABASE_ID });
  await assertFails(unauthedDb.collection("documents").doc("doc1").get());
});

test("Authenticated user can create a document owned by themselves", async () => {
  const aliceDb = testEnv.authenticatedContext("alice").firestore({ databaseId: DATABASE_ID });
  await assertSucceeds(
    aliceDb.collection("documents").doc("doc1").set({
      ownerId: "alice",
      title: "Quarterly Financial Analysis",
      format: "pdf",
      content: "This is the full text content of the financial report for Q3.",
      createdAt: new Date(),
      updatedAt: new Date(),
      collaborators: ["bob"],
      pageCount: 15,
      wordCount: 4200
    })
  );
});

test("Authenticated user cannot spoof ownerId on document creation", async () => {
  const aliceDb = testEnv.authenticatedContext("alice").firestore({ databaseId: DATABASE_ID });
  await assertFails(
    aliceDb.collection("documents").doc("doc1").set({
      ownerId: "bob",
      title: "Spoofed Doc",
      format: "txt",
      content: "Hello",
      createdAt: new Date(),
      updatedAt: new Date()
    })
  );
});

test("Collaborator can read document, but non-collaborator cannot", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminDb = context.firestore({ databaseId: DATABASE_ID });
    await adminDb.collection("documents").doc("doc1").set({
      ownerId: "alice",
      title: "Strategy 2026",
      format: "pdf",
      content: "Confidential roadmap",
      collaborators: ["bob"],
      createdAt: new Date(),
      updatedAt: new Date()
    });
  });

  const bobDb = testEnv.authenticatedContext("bob").firestore({ databaseId: DATABASE_ID });
  const charlieDb = testEnv.authenticatedContext("charlie").firestore({ databaseId: DATABASE_ID });

  await assertSucceeds(bobDb.collection("documents").doc("doc1").get());
  await assertFails(charlieDb.collection("documents").doc("doc1").get());
});

test("Collaborator can create insight and comment", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminDb = context.firestore({ databaseId: DATABASE_ID });
    await adminDb.collection("documents").doc("doc1").set({
      ownerId: "alice",
      title: "Strategy 2026",
      format: "pdf",
      content: "Confidential roadmap",
      collaborators: ["bob"],
      createdAt: new Date(),
      updatedAt: new Date()
    });
  });

  const bobDb = testEnv.authenticatedContext("bob").firestore({ databaseId: DATABASE_ID });

  await assertSucceeds(
    bobDb.collection("documents").doc("doc1").collection("insights").doc("ins1").set({
      documentId: "doc1",
      ownerId: "alice",
      collaborators: ["bob"],
      title: "Revenue Growth",
      content: "Projected 35% year-over-year revenue growth.",
      sourceReference: "Page 4, Paragraph 2",
      category: "key_takeaway",
      createdAt: new Date()
    })
  );

  await assertSucceeds(
    bobDb.collection("documents").doc("doc1").collection("comments").doc("com1").set({
      documentId: "doc1",
      ownerId: "alice",
      collaborators: ["bob"],
      authorId: "bob",
      authorName: "Bob Smith",
      text: "Please verify the numbers on page 4.",
      createdAt: new Date(),
      updatedAt: new Date()
    })
  );
});

test("Audit logs are append-only and cannot be updated", async () => {
  const aliceDb = testEnv.authenticatedContext("alice").firestore({ databaseId: DATABASE_ID });
  await assertSucceeds(
    aliceDb.collection("auditLogs").doc("log1").set({
      userId: "alice",
      userEmail: "alice@example.com",
      action: "summarize",
      details: "Generated executive summary for Q3 doc",
      timestamp: new Date()
    })
  );

  await assertFails(
    aliceDb.collection("auditLogs").doc("log1").update({
      details: "Altered log details"
    })
  );
});
