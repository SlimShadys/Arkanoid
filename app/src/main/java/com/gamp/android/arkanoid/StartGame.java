package com.gamp.android.arkanoid;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;

import com.gamp.android.arkanoid.game.Game;
import com.gamp.android.arkanoid.sound.Sound;

public class StartGame extends AppCompatActivity {

    private Game game;

    public static Activity activity = null;
    public static Sound sound;
    public static int orientation = 0;

    /**
     * onCreate per l'activity StartGame
     * Da qui avviamo il gioco vero e proprio, impostando come contentView, un oggetto di tipo Game
     * Inoltre impostiamo l'orientation della partita in base a come il Device è orientato
     * poco prima di premere il pulsante Play
     *
     * @param savedInstanceState default di Android
     */
    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle bundle = getIntent().getExtras();
        orientation = bundle.getInt("orientation");

        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }

        sound = new Sound(this);
        game = new Game(this, orientation);

        setContentView(game);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            final WindowInsetsController controller = getWindow().getInsetsController();

            if (controller != null)
                controller.hide(WindowInsets.Type.statusBars());
        } else {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }

        activity = this;

    }

    // Metti in pausa il gioco
    protected void onPause() {
        super.onPause();
        game.pauseGame();
    }

    // Riprendi il gioco
    protected void onResume() {
        super.onResume();
        game.resumeGame();
    }

    /**
     * Se premo indietro, mentre sto giocando, significa che l'utente vuole chiudere
     * la partita in corso, quindi stoppo l'Activity relativo al gioco
     */
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        sound.release();
        game.interruptGame();
        finish();
    }
}
