LIBRERIAS NECESARIAS PARA PROYECTO PYTHON:
 - Python 3.10... (cuando lo instalas hay que seleccionar la opcion de habilitar el path limit)
 - pip install opencv-python
 - pip install mediapipe
 - pip install tensorflow
 - pip install -U scikit-learn scipy matplotlib
 - pip install wheel
 - pip install pandas
 - pip install seaborn
 
Se ha modificado el archivo MediaPipeActivity y se ha mantenido parte del código original las funciones: onCreate, onResume, onPause.
También se han utilizado las comprobaciones que llaman a las siguientes funciones: multiHandLandmarks() y multiHandWorldLandmarks().
Se han añadido nuevos gestos en la función getGestureNameById como: zoom, brillo, contraste, aumentar, disminuir y un default para cuando no se encuentra un gesto. También sus respectivos getureToAction.
Se ha modidficado la función analyze y se ha añadido la función Bitmap downscaleBitmap2.
Se ha añadido la función runOnUiThread dentro de la ya existente getGestureNameById. 
Se han añadido las funciones zoomIn y zoomOut. 
Se han modificado las inputSource y se ha eliminado gran parte del código de la función onCreate. Además se ha añadido TextToSpeech y createNarrator para el narrador.
Se ha eliminado la función imageToBitmap y se ha añadido la función gestureRecognition.
Se ha creado la función onDestroy y se ha añadido la función checkLanguageSupport para el narrador en español.
Se han creado las funciones saveConfiguration y registerForActivityResult.
Se han añadido las funciones setBrightness, setExposure y setZoom.
Se ha reutilizado la función exposure y la manageDecrease.
Se ha creado una funcion que comprueba los permisos de la aplicación: checkCameraPermission.
Se ha creado la función resetValues y la función showPermissionsDialog.
Se ha aladido un manual de usuario y la licencia del proyecto.