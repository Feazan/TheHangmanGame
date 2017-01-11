package controller;

import apptemplate.AppTemplate;
import data.GameData;
import gui.Workspace;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.canvas.*;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import propertymanager.PropertyManager;
import ui.AppMessageDialogSingleton;
import ui.YesNoCancelDialogSingleton;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import static settings.AppPropertyType.*;
import static settings.InitializationParameters.APP_WORKDIR_PATH;

/**
 * @author Ritwik Banerjee
 * @author Feazan Yaseen
 */
public class HangmanController implements FileController {

    public enum GameState {
        UNINITIALIZED,
        INITIALIZED_UNMODIFIED,
        INITIALIZED_MODIFIED,
        ENDED
    }

    private AppTemplate appTemplate; // shared reference to the application
    private GameData    gamedata;    // shared reference to the game being played, loaded or saved
    private GameState   gamestate;   // the state of the game being shown in the workspace
    private Text[]      progress;    // reference to the text area for the word
    private FlowPane    alphabet;    // reference to the keyboard
    private HBox    guessedLetters;  // reference to the guessed letters
    private BorderPane  figurePane;  // container to display the namesake graphic of the (potentially) hanging person
    private Canvas      canvas;      // canvas to display the namesake graphic of the (potentially) hanging person
    private Button      hintButton;  // reference to the hint button
    private boolean     success;     // whether or not player was successful
    private int         discovered;  // the number of letters already discovered
    private Button      gameButton;  // shared reference to the "start game" button
    private Label       remains;     // dynamically updated label that indicates the number of remaining guesses
    private Path        workFile;

    public HangmanController(AppTemplate appTemplate, Button gameButton) {
        this(appTemplate);
        this.gameButton = gameButton;
    }

    public HangmanController(AppTemplate appTemplate) {
        this.appTemplate = appTemplate;
        this.gamestate = GameState.UNINITIALIZED;
    }

    public void enableGameButton() {
        if (gameButton == null) {
            Workspace workspace = (Workspace) appTemplate.getWorkspaceComponent();
            gameButton = workspace.getStartGame();
        }
        gameButton.setDisable(false);
    }

    public void disableGameButton() {
        if (gameButton == null) {
            Workspace workspace = (Workspace) appTemplate.getWorkspaceComponent();
            gameButton = workspace.getStartGame();
        }
        gameButton.setDisable(true);
    }

    public void setGameState(GameState gamestate) {
        this.gamestate = gamestate;
    }

    public GameState getGamestate() {
        return this.gamestate;
    }

    /**
     * In the homework code given to you, we had the line
     * gamedata = new GameData(appTemplate, true);
     * This meant that the 'gamedata' variable had access to the app, but the data component of the app was still
     * the empty game data! What we need is to change this so that our 'gamedata' refers to the data component of
     * the app, instead of being a new object of type GameData. There are several ways of doing this. One of which
     * is to write (and use) the GameData#init() method.
     */
    public void start() {
        gamedata = (GameData) appTemplate.getDataComponent();
        success = false;
        discovered = 0;

        Workspace gameWorkspace = (Workspace) appTemplate.getWorkspaceComponent();

        gamedata.init();
        setGameState(GameState.INITIALIZED_UNMODIFIED);
        HBox remainingGuessBox = gameWorkspace.getRemainingGuessBox();
        guessedLetters        = (HBox) gameWorkspace.getGameTextsPane().getChildren().get(1);
        alphabet              = gameWorkspace.getAlphabetPane();
        hintButton            = gameWorkspace.getHintButton();
        canvas                = gameWorkspace.getCanvas();
        remains = new Label(Integer.toString(GameData.TOTAL_NUMBER_OF_GUESSES_ALLOWED));
        remainingGuessBox.getChildren().addAll(new Label("Remaining Guesses: "), remains);
        initWordGraphics(guessedLetters);
        initAlphabetGraphics(alphabet);
        gamedata.setUsedHint(false);
        initHintButton(hintButton);
        play();
    }

    private void end() {
        appTemplate.getGUI().getPrimaryScene().setOnKeyTyped(null);
        gameButton.setDisable(true);
        setGameState(GameState.ENDED);
        appTemplate.getGUI().updateWorkspaceToolbar(gamestate.equals(GameState.INITIALIZED_MODIFIED));
        Platform.runLater(() -> {
            PropertyManager           manager    = PropertyManager.getManager();
            AppMessageDialogSingleton dialog     = AppMessageDialogSingleton.getSingleton();
            String                    endMessage = manager.getPropertyValue(success ? GAME_WON_MESSAGE : GAME_LOST_MESSAGE);
            if (dialog.isShowing())
                dialog.toFront();
            else
                dialog.show(manager.getPropertyValue(GAME_OVER_TITLE), endMessage);
            if (!success) {
                illuminateMissedCharacters();
            }
        });
        hintButton.setDisable(true);
    }

    private void initWordGraphics(HBox guessedLetters) {
        this.restoreWordGraphics(guessedLetters);
    }

    private void initAlphabetGraphics(FlowPane alphabetPane) {
        alphabet.getChildren().clear();
        for (int i = 0; i < 26; i++) {
            StackPane sp = new StackPane();
            sp.setPrefWidth(40);
            sp.setPrefHeight(40);
            sp.setBorder(new Border(new BorderStroke(Color.WHITE, BorderStrokeStyle.SOLID, null, null)));
            Character alphabetChar = (char) (i + 65);
            Character alphabetCharLower = (char) (alphabetChar + 32);
            Text letter = new Text(alphabetChar.toString());
            BackgroundFill backgroundFill = new BackgroundFill(Color.GREENYELLOW, null, null);
            if (gamedata.getGoodGuesses().contains(alphabetCharLower) ||
                    gamedata.getBadGuesses().contains(alphabetCharLower)) {
                backgroundFill = new BackgroundFill(Color.OLIVE, null, null);
            }

            sp.setBackground(new Background(backgroundFill));
            sp.getChildren().addAll(letter);
            alphabetPane.getChildren().add(sp);
        }
    }

    private void illuminateMissedCharacters() {
        HBox hbox = (HBox) guessedLetters.getChildren().get(0);
        for (int i = 0; i < gamedata.getTargetWord().length(); i++) {
            if (!gamedata.getGoodGuesses().contains(gamedata.getTargetWord().charAt(i))) {
                StackPane sp = (StackPane) hbox.getChildren().get(i);
                sp.setBackground(new Background(new BackgroundFill(Color.GRAY, null, null)));
                progress[i].setText(Character.toString(gamedata.getTargetWord().charAt(i)));
                progress[i].setVisible(true);
            }
        }
    }

    private void initHintButton(Button hintButton) {
        Set<Character> targetWordSet = new HashSet<>();
        for (int i = 0; i < gamedata.getTargetWord().length(); i++) {
            targetWordSet.add(gamedata.getTargetWord().charAt(i));
        }

        if (targetWordSet.size() > 7) {
            hintButton.setVisible(true);
            if (gamedata.isUsedHint()) {
                hintButton.setDisable(true);
            }
            else {
                // Set event handler using lambda
                hintButton.setOnMouseClicked(event -> {
                    gamedata.setUsedHint(true);
                    applyHint();
                    hintButton.setDisable(true);
                });
            }
        }
    }

    private void applyHint() {
        Platform.runLater(() -> {
            char hintChar = 0;
            for (int i = 0; i < gamedata.getTargetWord().length(); i++) {
                if (!gamedata.getGoodGuesses().contains(gamedata.getTargetWord().charAt(i))) {
                    hintChar = gamedata.getTargetWord().charAt(i);
                    break;
                }
            }
            AppMessageDialogSingleton messageDialog = AppMessageDialogSingleton.getSingleton();
            messageDialog.show("Hint", "The hint letter is " + Character.toString(hintChar));
            messageDialog.close();
            // Simulate key press to process hint
            try {
                Robot r = new Robot();
                r.keyPress(java.awt.event.KeyEvent.getExtendedKeyCodeForChar(hintChar));
                r.keyRelease(java.awt.event.KeyEvent.getExtendedKeyCodeForChar(hintChar));
            } catch (AWTException e) {
                // Skip
            }
        });
    }

    public void play() {
        disableGameButton();
        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                appTemplate.getGUI().updateWorkspaceToolbar(gamestate.equals(GameState.INITIALIZED_MODIFIED));
                appTemplate.getGUI().getPrimaryScene().setOnKeyTyped((KeyEvent event) -> {
                    char guess = event.getCharacter().charAt(0);
                    guess = Character.toLowerCase(guess);
                    if (!Character.isAlphabetic(guess)) {
                        // silently ignore any invalid characters
                        return;
                    }

                    if (!alreadyGuessed(guess)) {
                        boolean goodguess = false;
                        for (int i = 0; i < progress.length; i++) {
                            if (gamedata.getTargetWord().charAt(i) == guess) {
                                progress[i].setVisible(true);
                                gamedata.addGoodGuess(guess);
                                goodguess = true;
                                discovered++;
                            }
                        }
                        if (!goodguess) {
                            gamedata.addBadGuess(guess);
                            drawHangman();
                        }

                        success = (discovered == progress.length);
                        remains.setText(Integer.toString(gamedata.getRemainingGuesses()));

                        // Update the keyboard

                        StackPane keyboardLetter = (StackPane) alphabet.getChildren().get(guess - 97);
                        keyboardLetter.setBackground(new Background(new BackgroundFill(Color.OLIVE, null, null)));
                    }
                    setGameState(GameState.INITIALIZED_MODIFIED);
                });
                if (gamedata.getRemainingGuesses() <= 0 || success)
                    stop();
            }

            @Override
            public void stop() {
                super.stop();
                end();
            }
        };
        timer.start();
    }

    private void restoreGUI() {
        disableGameButton();
        Workspace gameWorkspace = (Workspace) appTemplate.getWorkspaceComponent();
        gameWorkspace.reinitialize();

        guessedLetters = (HBox) gameWorkspace.getGameTextsPane().getChildren().get(1);
        restoreWordGraphics(guessedLetters);

        alphabet = gameWorkspace.getAlphabetPane();
        initAlphabetGraphics(alphabet);

        hintButton = gameWorkspace.getHintButton();
        initHintButton(hintButton);

        canvas = gameWorkspace.getCanvas();
        drawHangman();

        HBox remainingGuessBox = gameWorkspace.getRemainingGuessBox();
        remains = new Label(Integer.toString(gamedata.getRemainingGuesses()));
        remainingGuessBox.getChildren().addAll(new Label("Remaining Guesses: "), remains);

        success = false;
        play();
    }

    private void restoreWordGraphics(HBox guessedLetters) {
        discovered = 0;
        char[] targetword = gamedata.getTargetWord().toCharArray();
        HBox hbox = new HBox();
        StackPane[] stackPanes = new StackPane[targetword.length];
        progress = new Text[targetword.length];
        for (int i = 0; i < progress.length; i++) {
            stackPanes[i] = new StackPane();
            stackPanes[i].setBackground(new Background(new BackgroundFill(Color.WHITE, null, null)));
            stackPanes[i].setPrefWidth(20);
            stackPanes[i].setPrefHeight(20);
            progress[i] = new Text(Character.toString(targetword[i]));
            progress[i].setVisible(gamedata.getGoodGuesses().contains(progress[i].getText().charAt(0)));
            if (progress[i].isVisible())
                discovered++;
            stackPanes[i].getChildren().addAll(progress[i]);
        }
        hbox.getChildren().addAll(stackPanes);
        hbox.setSpacing(4);
        guessedLetters.getChildren().addAll(hbox);
    }

    private boolean alreadyGuessed(char c) {
        return gamedata.getGoodGuesses().contains(c) || gamedata.getBadGuesses().contains(c);
    }

    private void drawHangman() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        gc.setFill(Color.BLACK);
        gc.setLineWidth(5);
        int badGuesses = gamedata.getBadGuesses().size();
        if (badGuesses > 0)
            gc.strokeLine(50, 300, 200, 300);
        if (badGuesses > 1)
            gc.strokeLine(50, 300, 50, 0);
        if (badGuesses > 2)
            gc.strokeLine(50, 0, 150, 0);
        if (badGuesses > 3)
            gc.strokeLine(150, 0, 150, 40);
        if (badGuesses > 4)
            gc.strokeOval(130, 40, 40, 40);
        if (badGuesses > 5)
            gc.strokeLine(150, 80, 150, 180);
        if (badGuesses > 6)
            gc.strokeLine(150, 180, 130, 250);
        if (badGuesses > 7)
            gc.strokeLine(150, 180, 170, 250);
        if (badGuesses > 8)
            gc.strokeLine(150, 120, 130, 170);
        if (badGuesses > 9)
            gc.strokeLine(150, 120, 170, 170);
        canvas.setVisible(true);
    }

    @Override
    public void handleNewRequest() {
        AppMessageDialogSingleton messageDialog   = AppMessageDialogSingleton.getSingleton();
        PropertyManager           propertyManager = PropertyManager.getManager();
        boolean                   makenew         = true;
        if (gamestate.equals(GameState.INITIALIZED_MODIFIED))
            try {
                makenew = promptToSave();
            } catch (IOException e) {
                messageDialog.show(propertyManager.getPropertyValue(NEW_ERROR_TITLE), propertyManager.getPropertyValue(NEW_ERROR_MESSAGE));
            }
        if (makenew) {
            appTemplate.getDataComponent().reset();                // reset the data (should be reflected in GUI)
            appTemplate.getWorkspaceComponent().reloadWorkspace(); // load data into workspace
            ensureActivatedWorkspace();                            // ensure workspace is activated
            workFile = null;                                       // new workspace has never been saved to a file
            ((Workspace) appTemplate.getWorkspaceComponent()).reinitialize();
            enableGameButton();
        }
        if (gamestate.equals(GameState.ENDED)) {
            appTemplate.getGUI().updateWorkspaceToolbar(false);
            Workspace gameWorkspace = (Workspace) appTemplate.getWorkspaceComponent();
            gameWorkspace.reinitialize();
        }
        // hangman should be hidden when creating new game.
        if (canvas != null) {
            canvas.setVisible(false);
        }
    }

    @Override
    public void handleSaveRequest() throws IOException {
        PropertyManager propertyManager = PropertyManager.getManager();
        if (workFile == null) {
            FileChooser filechooser = new FileChooser();
            Path        appDirPath  = Paths.get(propertyManager.getPropertyValue(APP_TITLE)).toAbsolutePath();
            Path        targetPath  = appDirPath.resolve(APP_WORKDIR_PATH.getParameter());
            filechooser.setInitialDirectory(targetPath.toFile());
            filechooser.setTitle(propertyManager.getPropertyValue(SAVE_WORK_TITLE));
            String description = propertyManager.getPropertyValue(WORK_FILE_EXT_DESC);
            String extension   = propertyManager.getPropertyValue(WORK_FILE_EXT);
            ExtensionFilter extFilter = new ExtensionFilter(String.format("%s (*.%s)", description, extension),
                    String.format("*.%s", extension));
            filechooser.getExtensionFilters().add(extFilter);
            File selectedFile = filechooser.showSaveDialog(appTemplate.getGUI().getWindow());
            if (selectedFile != null)
                save(selectedFile.toPath());
        } else
            save(workFile);
    }

    @Override
    public void handleLoadRequest() throws IOException {
        boolean load = true;
        if (gamestate.equals(GameState.INITIALIZED_MODIFIED))
            load = promptToSave();
        if (load) {
            PropertyManager propertyManager = PropertyManager.getManager();
            FileChooser     filechooser     = new FileChooser();
            Path            appDirPath      = Paths.get(propertyManager.getPropertyValue(APP_TITLE)).toAbsolutePath();
            Path            targetPath      = appDirPath.resolve(APP_WORKDIR_PATH.getParameter());
            filechooser.setInitialDirectory(targetPath.toFile());
            filechooser.setTitle(propertyManager.getPropertyValue(LOAD_WORK_TITLE));
            String description = propertyManager.getPropertyValue(WORK_FILE_EXT_DESC);
            String extension   = propertyManager.getPropertyValue(WORK_FILE_EXT);
            ExtensionFilter extFilter = new ExtensionFilter(String.format("%s (*.%s)", description, extension),
                    String.format("*.%s", extension));
            filechooser.getExtensionFilters().add(extFilter);
            File selectedFile = filechooser.showOpenDialog(appTemplate.getGUI().getWindow());
            if (selectedFile != null && selectedFile.exists())
                load(selectedFile.toPath());
            restoreGUI(); // restores the GUI to reflect the state in which the loaded game was last saved
        }
    }

    @Override
    public void handleExitRequest() {
        try {
            boolean exit = true;
            if (gamestate.equals(GameState.INITIALIZED_MODIFIED))
                exit = promptToSave();
            if (exit)
                System.exit(0);
        } catch (IOException ioe) {
            AppMessageDialogSingleton dialog = AppMessageDialogSingleton.getSingleton();
            PropertyManager           props  = PropertyManager.getManager();
            dialog.show(props.getPropertyValue(SAVE_ERROR_TITLE), props.getPropertyValue(SAVE_ERROR_MESSAGE));
        }
    }

    private void ensureActivatedWorkspace() {
        appTemplate.getWorkspaceComponent().activateWorkspace(appTemplate.getGUI().getAppPane());
    }

    private boolean promptToSave() throws IOException {
        PropertyManager            propertyManager   = PropertyManager.getManager();
        YesNoCancelDialogSingleton yesNoCancelDialog = YesNoCancelDialogSingleton.getSingleton();

        yesNoCancelDialog.show(propertyManager.getPropertyValue(SAVE_UNSAVED_WORK_TITLE),
                propertyManager.getPropertyValue(SAVE_UNSAVED_WORK_MESSAGE));

        if (yesNoCancelDialog.getSelection().equals(YesNoCancelDialogSingleton.YES))
            handleSaveRequest();

        return !yesNoCancelDialog.getSelection().equals(YesNoCancelDialogSingleton.CANCEL);
    }

    /**
     * A helper method to save work. It saves the work, marks the current work file as saved, notifies the user, and
     * updates the appropriate controls in the user interface
     *
     * @param target The file to which the work will be saved.
     * @throws IOException
     */
    private void save(Path target) throws IOException {
        appTemplate.getFileComponent().saveData(appTemplate.getDataComponent(), target);
        workFile = target;
        setGameState(GameState.INITIALIZED_UNMODIFIED);
        AppMessageDialogSingleton dialog = AppMessageDialogSingleton.getSingleton();
        PropertyManager           props  = PropertyManager.getManager();
        dialog.show(props.getPropertyValue(SAVE_COMPLETED_TITLE), props.getPropertyValue(SAVE_COMPLETED_MESSAGE));
    }

    /**
     * A helper method to load saved game data. It loads the game data, notified the user, and then updates the GUI to
     * reflect the correct state of the game.
     *
     * @param source The source data file from which the game is loaded.
     * @throws IOException
     */
    private void load(Path source) throws IOException {
        // load game data
        appTemplate.getFileComponent().loadData(appTemplate.getDataComponent(), source);

        // set the work file as the file from which the game was loaded
        workFile = source;

        // notify the user that load was successful
        AppMessageDialogSingleton dialog = AppMessageDialogSingleton.getSingleton();
        PropertyManager           props  = PropertyManager.getManager();
        dialog.show(props.getPropertyValue(LOAD_COMPLETED_TITLE), props.getPropertyValue(LOAD_COMPLETED_MESSAGE));

        setGameState(GameState.INITIALIZED_UNMODIFIED);
        Workspace gameworkspace = (Workspace) appTemplate.getWorkspaceComponent();
        ensureActivatedWorkspace();
        gameworkspace.reinitialize();
        gamedata = (GameData) appTemplate.getDataComponent();
    }
}
