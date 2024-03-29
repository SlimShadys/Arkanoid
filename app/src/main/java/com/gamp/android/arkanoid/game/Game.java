package com.gamp.android.arkanoid.game;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Insets;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;

import androidx.core.content.res.ResourcesCompat;

import android.os.CountDownTimer;
import android.util.Log;
import android.util.Size;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;

import com.gamp.android.arkanoid.BuildConfig;
import com.gamp.android.arkanoid.R;
import com.gamp.android.arkanoid.StartGame;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Random;
import java.util.function.Predicate;

import static android.content.Context.MODE_PRIVATE;
import static android.graphics.Color.RED;
import static android.graphics.Color.WHITE;

public class Game extends View implements SensorEventListener, View.OnTouchListener, Runnable {

    private final ArrayList<Ball> ballArrayList;
    private final ArrayList<Integer> launching;
    private final ArrayList<Brick> brickList;
    private final ArrayList<PowerUp> fallingPowerUp;
    private final ArrayList<RectF> lasers;

    private final HashMap<String, Bitmap> bitmaps = new HashMap<>();

    private static final int[] powerUpProbabilityThreshold = new int[PowerUp.weight.length];
    private static int RNG_Bound;

    private boolean flipperFeedback;
    private CountDownTimer PUT_flipper;
    private CountDownTimer PUT_lasers;

    private Bitmap background;

    private final boolean touchSensor;
    private boolean threadAlive = true;

    private final Context context;

    private final Paint levelText;
    private final Paint hearth;
    private final Paint paint;
    private final Paint scoreText;
    private final Paint startingBeam;
    private final Paint laserPaint;

    private final Random rand;

    // Rettangolo di supporto per disegnare un bitmap di dimensioni specificate
    private final RectF drawBox = new RectF();

    public static final RectF flipper = new RectF();

    private final Sensor accelerometer;
    private final SensorManager sManager;

    private final SharedPreferences mPrefs;

    private final Thread thread = new Thread();

    private float xDown;
    private float xFlipperDown;
    private double launchingAngle;

    public static float brickHeight;
    public static float brickWidth;
    public static float powerUpSide;
    public static float powerUpBrickCenter;

    public static int deviceHeight;
    public static int deviceWidth;
    private static float scale;

    private float levelBallSpeed;
    private final Point ballStartingPosition;

    private int level = 0;
    private int score = 0;
    private int life = 3;
    private final int orientation;

    /**
     * Costruttore dell'oggetto Game
     *
     * Qui inizializziamo tutte le varibili necessarie per il funzionamento del gioco
     * e impostiamo il gioco in modo che l'utente (Attraverso un tocco) faccia iniziare il livello
     *
     * @param  context  il contesto (this)
     * @param  orientation indica se il gioco deve essere avviato in landscape o portrait
     */
    public Game(Context context, int orientation) {
        super(context);

        ballArrayList = new ArrayList<>();
        launching = new ArrayList<>();
        brickList = new ArrayList<>();
        fallingPowerUp = new ArrayList<>();
        lasers = new ArrayList<>();

        paint = new Paint();
        hearth = new Paint();
        levelText = new Paint();
        scoreText = new Paint();
        startingBeam = new Paint();
        laserPaint = new Paint();
        rand = new Random();

        this.context = context;
        this.orientation = orientation;

        // Imposto l'accelerometro e il SensorManager
        sManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        // Controllo lo stato della Switch sul Touch tramite getSharedPreferences
        // @param save = Il nome della SharedPreferences
        // @param valueTouch = L'ID del Boolean della switch
        mPrefs = context.getSharedPreferences("save", 0);
        touchSensor = mPrefs.getBoolean("valueTouch", true);

        collectBitmaps();
        getSizeAndScale();
        setBackground(context);
        makePowerUpClocks();

        ballStartingPosition = new Point(deviceWidth / 2, (int) (deviceHeight - (300 + 20) * scale));
        brickHeight = 60 * scale;
        brickWidth = 150 * scale;
        powerUpSide = 60 * scale;
        powerUpBrickCenter = (float) ((150 - 60) / 2) * scale;

        // Inizializza l'array delle soglie di probabilità sommando i pesi di ciascun powerup
        {
            int l = powerUpProbabilityThreshold.length; // Lunghezza dell'Array
            powerUpProbabilityThreshold[0] = PowerUp.weight[0]; //Creo le soglie per i powerup
            for (int i = 1; i < l; i++) {
                // Somme parziali
                powerUpProbabilityThreshold[i] = powerUpProbabilityThreshold[i - 1] + PowerUp.weight[i];
            }
            // L'ultimo valore della scala
            RNG_Bound = powerUpProbabilityThreshold[l - 1];
        }

        resetLevel(0);

        this.setOnTouchListener(this);
    }

    /**
     * Inizializzazione Bitmaps
     *
     * Metodo che serve per collezionare tutti i Bitmap una sola volta e in un unico posto
     * In questo modo si risparmia una grande quantità di tempo e calcoli per frame
     */
    private void collectBitmaps() {
        int i;

        // salva i bitmap del flipper
        bitmaps.put("fl0", BitmapFactory.decodeResource(getResources(), R.drawable.flipper));
        bitmaps.put("fl1", BitmapFactory.decodeResource(getResources(), R.drawable.feedback_powerup));

        // salva i bitmap dei mattoni
        for (i = 0; i < 9; i++) { // Per ogni colore del brick
            for (int j = 1; j <= 3; j++) { //Per ogni vita del brick
                int d = getResources().getIdentifier("brick_" + i + "_" + j, "drawable", BuildConfig.APPLICATION_ID);
                bitmaps.put("br_" + i + "_" + j, BitmapFactory.decodeResource(getResources(), d));
            }
        }

        // salva i bitmap dei powerup
        for (i = 0; i < powerUpProbabilityThreshold.length; i++) {
            int d = getResources().getIdentifier("power_up_" + i, "drawable", BuildConfig.APPLICATION_ID);
            bitmaps.put("pu" + (i + 1), BitmapFactory.decodeResource(getResources(), d));
        }
    }

    /**
     * Impostiamo la scala (scale) relativa al Device
     *
     * All'interno di questo metodo, ci prendiamo la larghezza e la lunghezza del dispositivo
     * e ci salviamo queste informazioni. Successivamente, impostiamo una scala (ratio) che ci servirà
     * per definire le grandezze dei bricks, del flipper, della pallina, etc.
     * Il ratio è dato da "larghezza o lunghezza dispositivo / 1080"
     */
    // Per avere la stessa grandezza delle immagini/oggetti
    // indipendentemente dalla risoluzione del dispositivo
    private void getSizeAndScale() {
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

        // Controlliamo l'SDK. Se è < 30 (R), allora prendo le dimensioni dello schermo
        // attraverso le vecchie funzioni integrate prima dell'API 30.
        if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {

            Point size = new Point();
            Display display = windowManager.getDefaultDisplay();
            display.getSize(size); // Prendi le dimensione dello schermo
            deviceWidth = size.x;
            deviceHeight = size.y;

        } else { // Altrimenti utilizzo le funzioni dell'API >= 30.

            final WindowMetrics wm = windowManager.getCurrentWindowMetrics();
            Rect deviceScreen = wm.getBounds();

            // Gets all excluding insets
            final WindowInsets windowInsets = wm.getWindowInsets();
            Insets insets = windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars() | WindowInsets.Type.displayCutout());

            int insetsWidth = insets.right + insets.left;
            int insetsHeight = insets.top + insets.bottom;

            // Legacy size that Display.getSize reports
            final Size legacySize = new Size(deviceScreen.width() - insetsWidth,
                    deviceScreen.height() - insetsHeight);

            // Final device width & height
            deviceWidth = legacySize.getWidth();
            deviceHeight = legacySize.getHeight();
        }

        if (deviceWidth < deviceHeight) {
            scale = (float) deviceWidth / 1080;
        } else {
            scale = (float) deviceHeight / 1080;
        }
    }

    /**
     * Impostazione background
     *
     * Metodo che serve per impostare il background del gioco e inizializzare le scritte
     * presenti nel gioco (Livello, Score, Vite, etc).
     */
    private void setBackground(Context context) {
        background = Bitmap.createBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.background));
        background = Bitmap.createScaledBitmap(background, deviceWidth, deviceHeight, true);

        Typeface titiliumFont = ResourcesCompat.getFont(context, R.font.titilium_regular);
        Typeface atariFont = ResourcesCompat.getFont(context, R.font.atari_classic_smooth);

        hearth.setTextSize(50);

        laserPaint.setColor(RED);
        laserPaint.setAlpha(190);
        laserPaint.setAntiAlias(true);

        levelText.setColor(WHITE);
        levelText.setTextSize(60);
        levelText.setAntiAlias(true);
        levelText.setSubpixelText(true);
        levelText.setTypeface(Typeface.create(titiliumFont, Typeface.BOLD));

        scoreText.setColor(WHITE);
        scoreText.setTextSize(60);
        scoreText.setAntiAlias(true);
        scoreText.setSubpixelText(true);
        scoreText.setTextAlign(Paint.Align.CENTER);
        scoreText.setTypeface(Typeface.create(atariFont, Typeface.NORMAL));

        startingBeam.setColor(WHITE);
        startingBeam.setAlpha(100);
        startingBeam.setStrokeWidth(5);
        startingBeam.setAntiAlias(true);
    }

    /**
     * Metodo di onDraw della View
     *
     * Qui disegniamo ogni elemento all'interno del Canvas:
     * - Pallina
     * - Flipper
     * - Mattoni
     * - PowerUp
     *
     * @param  canvas  il Canvas in questione
     */
    protected void onDraw(final Canvas canvas) {

        super.onDraw(canvas);

        // Disegna il background sul Canvas
        canvas.drawBitmap(background, 0, 0, paint);

        // Disegna la traiettoria di lancio per la palla da lanciare
        if (!launching.isEmpty() && launchingAngle != 0) {
            Ball b = ballArrayList.get(launching.get(0));
            canvas.drawLine(b.cx, b.cy, b.cx + (float) (1000 * Math.cos(launchingAngle)), b.cy + (float) (1000 * Math.sin(launchingAngle)), startingBeam);
        }

        // Assicura che il flipper sia all'interno dello schermo
        if (flipper.right > deviceWidth) {
            flipper.offsetTo(deviceWidth - flipper.width(), flipper.top);
        } else if (flipper.left < 0) {
            flipper.offsetTo(0, flipper.top);
        }

        // Disegna i laser
        for (int i = 0; i < lasers.size(); i++) {
            RectF l = lasers.get(i);
            canvas.drawRect(l, laserPaint);
        }

        // Disegna il flipper
        canvas.drawBitmap(bitmaps.get("fl0"), null, flipper, paint);
        if (flipperFeedback) {
            canvas.drawBitmap(bitmaps.get("fl1"), null, flipper, paint);
        }

        // Disegna i mattoni in un rettangolo di supporto
        for (int i = 0; i < brickList.size(); i++) {
            Brick b = brickList.get(i);
            drawBox.set(b.getX(), b.getY(), b.getX() + brickWidth, b.getY() + brickHeight);
            canvas.drawBitmap(bitmaps.get("br_" + b.getColor() + "_" + b.getLife()), null, drawBox, paint);
        }

        // Disegna i powerup
        for (int i = 0; i < fallingPowerUp.size(); i++) {
            PowerUp p = fallingPowerUp.get(i);
            drawBox.set(p.getX(), p.getY(), p.getX() + powerUpSide, p.getY() + powerUpSide);
            canvas.drawBitmap(bitmaps.get("pu" + p.getId()), null, drawBox, paint);
        }

        // Disegna le palle
        for (int i = 0; i < ballArrayList.size(); i++) {
            ballArrayList.get(i).draw(canvas, WHITE);
        }

        // Scrivi il livello in alto a sinistra e il punteggio al centro dello schermo
        canvas.drawText("" + score, (float) (deviceWidth / 2), 1370, scoreText);
        canvas.drawText(getContext().getString(R.string.level) + level, 40, 120, levelText);

        // Disegna tanti cuori quante sono vite
        for (int i = 0; i < life; i++) {
            canvas.drawText("\uD83D\uDC9B", deviceWidth - 100 - 60 * i, 120, hearth);
        }

        run();

    }

    // Orologio dei powerup
    private void makePowerUpClocks() {
        PUT_flipper = new CountDownTimer(10000, 100) {
            public void onTick(long millisUntilFinished) {
                // Se il powerup sta per scadere, lampeggia (% 2) il flipper (contorno verde)
                flipperFeedback = millisUntilFinished <= 2000 && (millisUntilFinished / 100) % 2 == 0;
            }

            public void onFinish() {
                flipperFeedback = false;
                flipper.inset((flipper.width() - (200 * scale)) / 2, 0);
            }
        };

        PUT_lasers = new CountDownTimer(2700, 333) {
            public void onTick(long millisUntilFinished) {
                RectF l = new RectF(0, 0, 15 * scale, 80 * scale);
                l.offsetTo(flipper.left, flipper.bottom - 80 * scale);
                lasers.add(l);
                RectF r = new RectF(0, 0, 15 * scale, 80 * scale);
                r.offsetTo(flipper.right, flipper.bottom - 80 * scale);
                lasers.add(r);
            }

            public void onFinish() {
            }
        };
    }

    /**
     * Metodo di reset del livello
     *
     * Qui resettiamo il livello in base alla modalità richiesta
     *
     * @param mode  Mode 0 -> reset dopo aver completato un livello
     *              Mode 1 -> reset dopo aver perso una vita
     */
    private void resetLevel(int mode) {

        powerUpCleaner(mode);

        if (mode == 0) {
            ++level;
            generateBricks();
            levelBallSpeed = (14 + (level * 2)) * scale;
        }

        flipper.offsetTo((deviceWidth - flipper.width()) / 2, deviceHeight - 300 * scale);
        ballArrayList.add(new Ball(ballStartingPosition.x, ballStartingPosition.y, 20 * scale));

        launching.add(0);
        launchingAngle = 0;
    }

    /**
     * Metodo di reset dei PowerUp
     *
     * Qui resettiamo gli array contenenti i PowerUp
     * in base alla modalità richiesta
     *
     * @param mode  Mode 0 -> reset dopo aver completato un livello
     *              Mode 1 -> reset dopo aver perso una vita
     */
    private void powerUpCleaner(int mode) {
        // Mode 0
        if (mode == 0) {
            fallingPowerUp.clear();
            ballArrayList.clear();
            lasers.clear();
            flipperFeedback = false;
            flipper.set(0, 0, 200 * scale, 40 * scale);
            PUT_flipper.cancel();
            PUT_lasers.cancel();
        }
        // Mode 1
        // ??Non ci sono powerup che necessitano essere resettati dopo aver perso una vita??
    }

    /**
     * Metodo di generazione dei brick
     *
     * Generiamo i brick, prendendo un colore a caso e scegliendo, inoltre, tra il colore
     * più chiaro (+1) e il colore più scuro (-1) in modo tale da colorare i brick
     * con una palette omogenea
     * Ad ogni 5 livelli, passiamo alla skin (relativa al Brick) della vita successiva
     */
    private void generateBricks() {
        int hue = rand.nextInt(9); // Rand da 0 a 8 - Colore di riferimento
        int palette = rand.nextInt(3) + 2; // Rand da 2 a 4 - Numero di colori a partire dal colore di riferimento

        // Per fare 5 colonne
        float columnSize = (float) deviceWidth / 5;

        // 4 righe - 5 colonne
        for (int i = 3; i < 7; i++) {
            for (int j = 0; j < 5; j++) {
                int color = (hue + rand.nextInt(palette)) % 9;
                // Level = Ad ogni 5 livelli
                brickList.add(new Brick(j * columnSize + (columnSize - brickWidth) / 2, i * 100 * scale, (level / 5) + 1, color));
            }
        }
    }

    /**
     * Controlla la collisione con tra brick e pallina
     *
     * Ci prendiamo il riferimento alla pallina e controlliamo per ogni Brick
     * presente nel gioco, se quest'ultima ha toccato i Brick in questione
     *
     * @param ball La pallina con cui vogliamo controllare la collisione
     * @return True se la collisione è avvenuta, False altrimenti
     */
    private boolean isCollideWithBrick(Ball ball) {
        for (int i = 0; i < brickList.size(); i++) {
            Brick b = brickList.get(i);
            if (ball.collideWith(b)) {
                if (b.loseLife(1)) {
                    brickList.remove(b);
                    whatsInside(b);
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Controlla la collisione con tra brick e laser
     *
     * Ci prendiamo il riferimento al laser e controlliamo per ogni Brick
     * presente nel gioco, se quest'ultimo ha toccato i Brick in questione
     *
     * @param laser Il laser con cui vogliamo controllare la collisione
     * @return True se la collisione è avvenuta, False altrimenti
     */
    private boolean isCollideWithBrick(RectF laser) {
        for (int i = 0; i < brickList.size(); i++) {
            Brick b = brickList.get(i);
            // Collisione rettangolo-rettangolo
            if (laser.top <= b.getY() + brickHeight && laser.bottom >= b.getY() && laser.left <= b.getX() + brickWidth && laser.right >= b.getX()) {
                if (b.loseLife(1)) {
                    brickList.remove(b);
                    whatsInside(b);
                }
                scoreUpdate(80);
                return true;
            }
        }
        return false;
    }

    /**
     * Sorteggio del PowerUp da mostrare
     *
     * Ogni volta che becchiamo un Brick con la pallina, generiamo una probabilità
     * che esca o meno un determinato PowerUp
     *
     * @param b Il brick con cui vogliamo generare un PowerUp
     */
    // Sorteggia un powerUp (0: niente)
    private void whatsInside(Brick b) {
        // Mi scegli uno a caso dalla scala dei powerUp
        int randomInt = rand.nextInt(RNG_Bound);
        int id = 0;
        // Finche c'è un altro powerUp da controllare,
        // controlla se il numero sorteggiato è minore della soglia dell'n-simo powerup
        while (id < powerUpProbabilityThreshold.length) {
            // Semplicemente va a decidere in che intervallo va a cadere il valore
            if (randomInt < powerUpProbabilityThreshold[id]) {
                break;
            }
            id++;
        }

        if (id != 0) {
            fallingPowerUp.add(new PowerUp(b.getX() + powerUpBrickCenter, b.getY(), id, 8 * scale));
        }
    }

    /**
     * Controllo delle vite in gioco
     *
     * In questa fase controlliamo le vite del giocatore.
     * Se le vite < 0, allora il gioco è finito; Quindi, avviamo la procedura
     * per salvare il punteggio, per mostrare a video la possibilità di
     * riavviare la partita o di tornare al Menù.
     *
     */
    // Perdi una vita e interrompi il gioco in caso di perdita
    private void loseLife() {
        --life;

        // Caso in cui l'utente ha più di una vita
        if (life > 0) {
            StartGame.sound.playLostLife(); // Se l'utente perde una vita, attiva il suono
            resetLevel(1);

            // Caso in cui hai perso
        } else {
            StartGame.sound.playLostLife(); // Se il gioco è finito, attiva il suono relativo

            int scoreGame = mPrefs.getInt("Score", 0);

            if (score > scoreGame) {
                // Cambio il valore score con quello più aggiornato
                SharedPreferences.Editor editor = context.getSharedPreferences("save", MODE_PRIVATE).edit();
                editor.putInt("Score", score);
                editor.apply();

                // Get Firebase user
                FirebaseAuth mAuth = FirebaseAuth.getInstance();
                FirebaseUser user = mAuth.getCurrentUser();

                // Get GoogleAcc user
                GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(getContext());
                if (account != null && user != null) {
                    String personEmail = account.getEmail(); //Get user email
                    String personName = account.getDisplayName(); //Get user name

                    // Get reference to our Firebase db and write into it
                    DatabaseReference rootRef = FirebaseDatabase.getInstance().getReference();
                    rootRef.child("Users").child(user.getUid()).child("Email").setValue(personEmail);
                    rootRef.child("Users").child(user.getUid()).child("Name").setValue(personName);
                    rootRef.child("Users").child(user.getUid()).child("Score").setValue(score);
                }
            }

            // Creo un oggetto AlertDialog, che mi permette di visualizzare
            // un dialog riguardo la scelta di iniziare una nuova partita o meno
            AlertDialog alertDialog = new AlertDialog.Builder(context).create();
            alertDialog.setCancelable(false);
            alertDialog.setCanceledOnTouchOutside(false);
            alertDialog.setTitle(getContext().getString(R.string.gameOver));
            alertDialog.setMessage(getContext().getString(R.string.startNewMatch));

            // Codice se l'utente clicca "Si"
            alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, getContext().getString(R.string.yes), (dialog, which) -> {

                // Termina l'attività del gioco e iniziane un'altra
                StartGame.activity.finish();
                Intent intent = new Intent(context, StartGame.class)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.putExtra("orientation", orientation);
                context.startActivity(intent);
            });

            alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, getContext().getString(R.string.no), (dialog, which) -> {
                // Termina l'attività del gioco (automaticamente torna al Menù)
                StartGame.activity.finish();
            });
            alertDialog.show();
        }
    }

    private void scoreUpdate(int points) {
        score += points;

        if (points > 0 && score % 1000 == 0) {
            StartGame.sound.playScoreSound();
        }
    }
    /**
     * Aggiornamento del gioco
     *
     * Questo è una parte fondamentale del gioco.
     * Qui controlliamo se il livello è completato, altrimenti muoviamo
     * gli oggetti necessari, quali:
     * - Pallina
     * - PowerUp
     * - Laser
     *
     */
    private void update() {
        // Controlla se il livello è stato completato
        if (brickList.isEmpty()) {
            StartGame.sound.playWin();
            resetLevel(0);
        } else {
            moveBalls();
            movePowerUps();
            moveLasers();
        }
    }


    /**
     * Aggiornamento delle palline in movimento
     *
     * Qui aggiorniamo la posizione delle palline in movimento,
     * controllando se le palline sono in gioco o meno.
     *
     */
    // Aggiorna la posizione delle palle in movimento
    private void moveBalls() {
        for (int i = 0; i < ballArrayList.size(); i++) {
            // Non trattare le palle in fase di lancio
            // Tratto solo le palline che sono già lanciate
            if (!launching.contains(i)) {
                Ball b = ballArrayList.get(i);
                // Dopo il movimento se...
                if (b.move()) {
                    // ...la palla non è morta, controlla eventuali sue collisioni con mattoni
                    if (isCollideWithBrick(b)) {
                        scoreUpdate(80);
                    }
                } else {
                    // ...la palla è morta, eliminala dall'ArrayList
                    ballArrayList.remove(b);
                    if (ballArrayList.isEmpty()) {
                        // Era l'unica in gioco, togli una vita
                        loseLife();
                    }
                }
            }
        }
    }

    /**
     * Aggiornamento dei PowerUp in movimento
     *
     * Qui aggiorniamo la posizione dei PowerUp in movimento,
     * controllando se sono in gioco o meno.
     *
     */
    private void movePowerUps() {
        fallingPowerUp.removeIf(p -> {
            if(p.move()) {
                powerUpActivate(p.getId());
                return true;
            } else {
                return false;
            }
        });
    }

    /**
     * Aggiornamento dei Laser in movimento
     *
     * Qui aggiorniamo la posizione dei Laser in movimento,
     * controllando se sono in gioco o meno.
     *
     */
    private void moveLasers() {
        lasers.removeIf(l -> {
            l.offset(0,-24);
            return l.bottom <= 0 || isCollideWithBrick(l);
        });
    }

    /**
     * Attivazione PowerUp
     *
     * Qui attiviamo i PowerUp in base all'ID passato alla funzione.
     *
     * @param id identifica un PowerUp (Flipper più lungo/corto, bonus palline, etc.)
     */
    private void powerUpActivate(int id) {
        switch (id) {
            case 0:
                return;
            case 1:
                scoreUpdate(200);
                flipper.inset((flipper.width() - (300 * scale)) / 2, 0);
                PUT_flipper.cancel();
                PUT_flipper.start();
                break;
            case 2:
                scoreUpdate(200);
                int n = ballArrayList.size();
                Ball b = ballArrayList.get(n - 1);
                // Mi serve per avere un tetto massimo di palline
                for (int i = n; i < 3; i++) {
                    ballArrayList.add(new Ball(b.cx, b.cy, 20 * scale));
                    ballArrayList.get(i).setVelocity(levelBallSpeed, rand.nextDouble() * 2 * Math.PI);
                }
                break;
            case 3:
                scoreUpdate(-300);
                flipper.inset((flipper.width() - (120 * scale)) / 2, 0);
                PUT_flipper.cancel();
                PUT_flipper.start();
                break;
            case 4:
                scoreUpdate(200);
                PUT_lasers.cancel();
                PUT_lasers.start();
                break;
        }
    }

    public void pauseGame() {
        sManager.unregisterListener(this);
    }

    public void resumeGame() {
        sManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
    }

    /**
     * Accelerometro
     *
     * Catturiamo l'evento (cambiamento) dell'accelerometro e di conseguenza
     * spostiamo il Flipper in base alla posizione del sensore.
     *
     * @param event evento verificatosi al movimento dell'accelerometro
     */
    @Override
    public void onSensorChanged(SensorEvent event) {
        // "touchSensor" è una variabile collegata alla Switch del Touch presente
        // nelle opzioni. Se la switch è disattivata, utilizza l'accelerometro
        if (launching.isEmpty() && !touchSensor) {
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                flipper.offset(-4 * event.values[0], 0);
            }
        }
    }

    // Metodo necessario solamente perchè la classe Game
    // implementa SensorEventListener, la quale necessita
    // dell'override del metodo astratto onAccuracyChanged
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    // Metodo necessario solamente perchè la classe Game
    // estende la classe View, la quale necessita
    // dell'override del metodo astratto performClick
    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    /**
     * Touch
     *
     * Catturiamo l'evento (tocco su schermo) del Touch e di conseguenza
     * spostiamo il Flipper in base alla posizione del nostro dito.e
     * Inoltre, ci serve anche a inizio partita, per mostrare al giocatore
     * l'angolo in cui la pallina andrà a finire non appena rilasciamo il dito.
     *
     * @param v la View a cui si sta facendo riferimento
     * @param event evento verificatosi al tocco sullo schermo
     */
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (launching.isEmpty()) {
            if (touchSensor) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        xDown = event.getX();
                        xFlipperDown = flipper.left;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        flipper.offsetTo((xFlipperDown + (event.getX() - xDown)), flipper.top);
                        break;
                }
            }
        } else {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_MOVE:
                    Ball b = ballArrayList.get(launching.get(0));
                    // Angolo che c'è tra la palla e il dito
                    // Scelgo atan2, poichè atan mi restituisce valori tra -pi/2 e pi/2.
                    // mentre con atan2, mi restituisce valori tra -pi e pi (che serve a noi).
                    // Questo perchè altrimenti, c'è la possibilità che nel caso in cui mettessi
                    // il dito sulla parte superiore sinistra, il programma me lo registra sulla inferiore destra
                    launchingAngle = Math.atan2(-Math.abs(event.getY() - b.cy), event.getX() - b.cx);
                    launchingAngle = Math.min(Math.toRadians(-15), Math.max(launchingAngle, Math.toRadians(-165)));
                    break;
                case MotionEvent.ACTION_UP:
                    if (launchingAngle != 0) {
                        ballArrayList.get(launching.get(0)).setVelocity(levelBallSpeed, launchingAngle);
                        launching.remove(0);
                    }
                    break;
            }
        }
        return true;
    }

    /**
     * Aggiornamento scale
     *
     * Catturiamo l'evento in cui le dimensioni del telefono si aggiornano
     * (di solito quando si passa da landscape a portrait) e aggiorniamo il
     * fattore 'scale' di conseguenza.
     *
     * @param w la nuova larghezza del telefono
     * @param h la nuova lunghezza del telefono
     * @param oldw la vecchia larghezza del telefono
     * @param oldh la vecchia lunghezza del telefono
     */
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        deviceWidth = w;
        deviceHeight = h;

        if (deviceWidth < deviceHeight) {
            scale = (float) deviceWidth / 1080;
        } else {
            scale = (float) deviceHeight / 1080;
        }

        Log.d("DEBUG", "Scale in onSizeChanged: " + scale);

    }

    /**
     * Thread Game
     *
     * Questo è il cuore del gioco. Da qui passa ogni funzione vista precedentemente
     * E' un Thread che invoca il metodo 'update()' il quale muove le palline, laser, etc.
     * e disegna a schermo quello contenuto nell'onDraw.
     * Inoltre se il Thread non è più accessibile (visibile tramite la variabile 'threadAlive')
     * allora interrompiamo il Thread stesso, interrompendo il gioco.
     *
     */
    @Override
    public void run() {
        if (threadAlive) {
            update();
            postInvalidateOnAnimation();
        }
    }

    /**
     * Interruzione Thread
     *
     * Questo metodo è invocato quando vogliamo interrompere il gioco.
     * Viene lanciato quando premiamo il tasto indietro.
     * Inoltre viene aggiornata la variabile che indica se il Thread è
     * ancora valido o meno, in modo tale da non far continuare
     * l'esecuzione del Thread stesso nel metodo 'run()'
     *
     */
    public void interruptGame() {
        threadAlive = false;
        thread.interrupt();
        Log.d("W", "Thread ended!");
    }
}
