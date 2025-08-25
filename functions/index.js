/*const functions = require('firebase-functions')
    .region('europe-west1'); // Especificar la región aquí
const admin = require('firebase-admin');
admin.initializeApp();

exports.sendMarkerNotification = functions.database.ref('/markers/{markerId}')
    .onCreate(async (snapshot, context) => {
        try {
            const markerData = snapshot.val();
            const creatorUid = markerData.creatorUid;

            console.log('Nuevo marcador creado por:', creatorUid);

            // Si no hay creador, salir
            if (!creatorUid) {
                console.log('No se encontró creatorUid en los datos del marcador');
                return null;
            }

            // Obtener el UID del usuario a notificar
            const userRef = admin.database().ref(`users/${creatorUid}/notifyUserId`);
            const userSnapshot = await userRef.once('value');
            const notifyUserId = userSnapshot.val();

            if (!notifyUserId) {
                console.log('No se encontró usuario para notificar para el creador:', creatorUid);
                return null;
            }

            console.log('Usuario a notificar:', notifyUserId);

            // Obtener el token FCM del usuario a notificar
            const tokenRef = admin.database().ref(`users/${notifyUserId}/fcmToken`);
            const tokenSnapshot = await tokenRef.once('value');
            const token = tokenSnapshot.val();

            if (!token) {
                console.log('No se encontró token FCM para el usuario:', notifyUserId);
                return null;
            }

            // Crear el mensaje de notificación
            const message = {
                notification: {
                    title: '¡Nuevo Beso en el Mapa! 💋',
                    body: 'Se ha agregado una nueva polaroid al mapa'
                },
                token: token,
                data: {
                    type: 'new_marker',
                    markerId: context.params.markerId,
                    latitude: markerData.latlng.lat.toString(),
                    longitude: markerData.latlng.lng.toString()
                }
            };

            // Enviar la notificación
            const response = await admin.messaging().send(message);
            console.log('Notificación enviada exitosamente:', response);
            return null;

        } catch (error) {
            console.error('Error en sendMarkerNotification:', error);
            return null;
        }
    });
    */
    const functions = require('firebase-functions')
        .region('europe-west1');
    const admin = require('firebase-admin');
    admin.initializeApp();

    exports.sendMarkerNotification = functions.database.ref('/markers/{markerId}')
        .onCreate(async (snapshot, context) => {
            try {
                const markerData = snapshot.val();
                const creatorUid = markerData.creatorUid;

                console.log('Nuevo marcador creado por:', creatorUid);

                // Variable para modo prueba. false = producción
                const TEST_MODE = false;
                const TEST_UID = 'zZDxTAG9TheoDHJsw5ZidM2kALj2'; // Tu UID

                let notifyUserId;
                let notificationTitle;

                if (TEST_MODE) {
                    // Modo prueba: enviar a mi propio dispositivo
                    notifyUserId = TEST_UID;
                    notificationTitle = '¡Nuevo Beso en el Mapa! 💋 [PRUEBA]';
                    console.log('Modo prueba: Notificando a mi propio dispositivo');
                } else {
                    // Modo producción: enviar al otro usuario
                    // Obtener el UID del usuario a notificar desde la base de datos
                    const userRef = admin.database().ref(`users/${creatorUid}/notifyUserId`);
                    const userSnapshot = await userRef.once('value');
                    notifyUserId = userSnapshot.val();
                    notificationTitle = '¡Nuevo Beso en el Mapa! 💋';
                    console.log('Modo producción: Notificando al usuario:', notifyUserId);
                }

                if (!notifyUserId) {
                    console.log('No se encontró usuario para notificar');
                    return null;
                }

                // Obtener el token FCM del usuario a notificar
                const tokenRef = admin.database().ref(`users/${notifyUserId}/fcmToken`);
                const tokenSnapshot = await tokenRef.once('value');
                const token = tokenSnapshot.val();

                if (!token) {
                    console.log('No se encontró token FCM para el usuario:', notifyUserId);
                    return null;
                }

                // Crear el mensaje de notificación
                const message = {
                    notification: {
                        title: notificationTitle,
                        body: TEST_MODE ?
                            'Hay una nueva polaroid al mapa (modo prueba)' :
                            'Hay una nueva polaroid al mapa'
                    },
                    token: token,
                    data: {
                        type: 'new_marker',
                        markerId: context.params.markerId,
                        latitude: markerData.latlng.lat.toString(),
                        longitude: markerData.latlng.lng.toString(),
                        test: TEST_MODE ? 'true' : 'false'
                    }
                };

                // Enviar la notificación
                const response = await admin.messaging().send(message);
                console.log('Notificación enviada exitosamente a:', notifyUserId);
                return null;

            } catch (error) {
                console.error('Error en sendMarkerNotification:', error);
                return null;
            }
        });