const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

const db = admin.firestore();
const messaging = admin.messaging();

/**
 * Cloud Function: Send FCM push notification when a new call is created.
 * Triggers on: calls/{callId} document creation
 *
 * This ensures the callee receives a notification even if the app is killed/background.
 * The Firestore listener (listenForIncomingCalls) handles the in-app case,
 * but FCM handles the background/killed case.
 */
exports.onCallCreated = functions.firestore
  .document("calls/{callId}")
  .onCreate(async (snap, context) => {
    const callId = context.params.callId;
    const data = snap.data();

    const calleeUid = data.calleeUid;
    const callerNom = data.callerNom || "Appel entrant";
    const callType = data.type || "video";

    if (!calleeUid) {
      console.log("No calleeUid in call document, skipping FCM");
      return null;
    }

    console.log(`New call ${callId}: ${callerNom} -> ${calleeUid} (${callType})`);

    // Find callee's FCM tokens
    const tokensSnapshot = await db
      .collection("fcm_tokens")
      .where("uid", "==", calleeUid)
      .get();

    if (tokensSnapshot.empty) {
      // Fallback: check users collection
      const userDoc = await db.collection("users").doc(calleeUid).get();
      const fcmToken = userDoc.data()?.fcmToken;
      if (!fcmToken) {
        console.log(`No FCM token found for ${calleeUid}`);
        return null;
      }
      return sendCallNotification(fcmToken, callId, data.callerUid, callerNom, callType);
    }

    // Send to all tokens for this user (multiple devices)
    const promises = [];
    tokensSnapshot.forEach((doc) => {
      const token = doc.data().token;
      if (token) {
        promises.push(
          sendCallNotification(token, callId, data.callerUid, callerNom, callType)
        );
      }
    });

    return Promise.all(promises);
  });

/**
 * Send a high-priority FCM data message for incoming call.
 * Data messages wake up the app and are handled by DiaSmartFCMService.
 */
async function sendCallNotification(token, callId, callerUid, callerNom, callType) {
  const message = {
    token: token,
    data: {
      type: "incoming_call",
      callId: callId,
      callerUid: callerUid || "",
      callerNom: callerNom,
      callType: callType,
    },
    android: {
      priority: "high",
      ttl: 30000, // 30 seconds TTL — calls expire quickly
    },
  };

  try {
    const response = await messaging.send(message);
    console.log(`FCM sent to ${token.substring(0, 10)}...: ${response}`);
    return response;
  } catch (error) {
    console.error(`FCM error for ${token.substring(0, 10)}...:`, error.message);
    // If token is invalid, clean it up
    if (
      error.code === "messaging/invalid-registration-token" ||
      error.code === "messaging/registration-token-not-registered"
    ) {
      const snapshot = await db
        .collection("fcm_tokens")
        .where("token", "==", token)
        .get();
      snapshot.forEach((doc) => doc.ref.delete());
      console.log("Cleaned up invalid token");
    }
    return null;
  }
}

/**
 * Auto-cleanup: Delete call documents older than 1 hour.
 * Runs every 30 minutes to prevent Firestore bloat.
 */
exports.cleanupOldCalls = functions.pubsub
  .schedule("every 30 minutes")
  .onRun(async () => {
    const oneHourAgo = new Date(Date.now() - 60 * 60 * 1000);
    const oldCalls = await db
      .collection("calls")
      .where("createdAt", "<", admin.firestore.Timestamp.fromDate(oneHourAgo))
      .limit(100)
      .get();

    const batch = db.batch();
    oldCalls.forEach((doc) => batch.delete(doc.ref));

    if (!oldCalls.empty) {
      await batch.commit();
      console.log(`Cleaned up ${oldCalls.size} old call documents`);
    }
    return null;
  });
