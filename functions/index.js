const { onValueCreated } = require("firebase-functions/database");
const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

// Define los dos UIDs
const uidA = "zZDxTAG9TheoDHJsw5ZidM2kALj2"; // Jesús
const uidB = "JFYdmUPY93eETaxw0TLyoVgktw22"; // Marisol

exports.sendMarkerNotification = onValueCreated(
  { ref: "/markers/{markerId}", region: "europe-west1" },
  async (event) => {
    const markerData = event.data.val();
    const creatorUid = markerData.creatorUid;

    if (!creatorUid) {
      console.log("No se encontró creatorUid en el marcador.");
      return null;
    }

    // Determina quién debe recibir la notificación
    const recipientUid = creatorUid === uidA ? uidB : uidA;
    const senderName = creatorUid === uidA ? "Jesús" : "Marisol";

    const tokenSnapshot = await admin.database()
      .ref(`/users/${recipientUid}/fcmToken`)
      .once("value");

    const targetToken = tokenSnapshot.val();

    if (!targetToken) {
      console.log("No se encontró token FCM para el usuario.");
      return null;
    }

    const message = {
      notification: {
        title: "📍 ¡Nueva beso en el álbum!",
        body: `¡${senderName} ha subido una nueva Polariod!`,
      },
      data: {
        lat: markerData.latlng.lat.toString(),
        lng: markerData.latlng.lng.toString(),
        imageUrl: markerData.image,
      },
      android: {
          priority: "high",
          notification: {
            sound: "default"
          }
        },
        token: targetToken

    };

    try {
      const response = await admin.messaging().send(message);
      console.log("Notificación enviada:", response);
    } catch (error) {
      console.error("Error al enviar la notificación:", error);
    }

    return null;
  }
);