package app.orvalho.launcher;

import android.app.Application;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Vigia o volume do sistema para aplicar o limite máximo e o modo noturno.
        VolumeGuard.start(this);
        // Calcula as cores de destaque dos apps em segundo plano, para a tela abrir sem travar.
        final App ctx = this;
        Thread t = new Thread(() -> Icons.warm(ctx), "orvalho-warm");
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }
}
