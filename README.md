# TFG_DMAE

  

Este proyecto ha utilizado recursos de otros trabajos que se encuentran protegidos bajo la licencia Apache 2.0, los cambios realizados sobre dichos trabajos son los siguientes:

  

<h3><a  href="https://github.com/kinivi/hand-gesture-recognition-MediaPipe"  target="_blank">Hand-gesture-recognition-mediapipe</a></h3>

<ul>
	<li>Se ha modificado el fichero app.py, eliminando el código que no era necesario (gestión de la webcam del ordenador y toda la gestión visual de los landmarks) además de cambiar el método main para que sea capaz de recibir como argumento un JSON con información de landmarks y devolver el identificador del gesto identificado.</li>
	<li> Se han utilizado los ficheros asociados a la generación y procesamiento del modelo de entrenamiento (keypoint_classification.ipynb y keypoint_classifier.py) para que sea capaz de reconocer los gestos utilizados en la aplicación y que en caso de que el porcentaje de acierto sea menor que un 90% devuelva un identificador asociado a que no se ha reconocido el gesto.</li>
	<li> Todos los ficheros que no se han mencionado en los puntos anteriores han sido eliminados ya que no 			eran necesarios en el proyecto.</li>
</ul>

<h3><a  href="https://github.com/google/mediapipe/tree/master/mediapipe/examples/android/solutions/hands/src/main/java/com/google/mediapipe/examples/hands"  target="_blank">Mediapipe Hands</a></h3>
<ul>
<li>Se ha eliminado tanto el procesamiento en streaming como el procesamiento de vídeo de la clase MainActivity.py ya que este proyecto realiza la detección de gestos mediante imágenes (frame a frame). Además, esta clase se ha modificado para eliminar la gestión visual de los landmarks y añadir la funcionalidad específica de la aplicación (realizar una acción en base al gesto reconocido).</li>
<li> Las clases HandsResultGlRenderer.java y HandsResultImageView.java no se han utilizado. </li>
