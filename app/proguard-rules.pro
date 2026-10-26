# Room gera as implementações dos DAOs; mantém as entidades intactas.
-keep class app.financas.data.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep class * extends androidx.work.ListenableWorker { <init>(...); }
