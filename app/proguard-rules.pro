# ProGuard / R8 optimization rules for Melody

# Retain attributes needed for stack traces and retracing
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod,*Annotation*
-renamesourcefileattribute SourceFile

# App entry points and MediaSessionService
-keep class com.amarthapa.melody.PlaybackService { *; }

# UI State models and ViewModels
-keep class com.amarthapa.melody.MelodyViewModel { *; }
-keep class com.amarthapa.melody.AudioTrack { *; }
-keep class com.amarthapa.melody.MelodyUiState { *; }
