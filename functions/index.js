const functions = require("firebase-functions");
const admin = require("firebase-admin");
admin.initializeApp();

exports.sendNotification = functions.storage.object().onFinalize(
  async (object) => {
    const payload = {
      notification: {
        title: "Nuevo archivo subido",
        body: `Se ha subido un nuevo archivo: ${object.name}`,
      },
    };

    const response = await admin.messaging().sendToTopic(
      "uploads",
      payload,
    );
    console.log("Notificación enviada:", response);
  },
);

