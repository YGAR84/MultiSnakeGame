package ru.nsu.a.lyamin.view;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import ru.nsu.a.lyamin.discoverer.Discoverer;
import ru.nsu.a.lyamin.message_decoder.SnakesProto;
import ru.nsu.a.lyamin.message_manager.HostInfo;
import ru.nsu.a.lyamin.message_manager.MessageManager;
import ru.nsu.a.lyamin.snake_game.Point;
import ru.nsu.a.lyamin.snake_game.Snake;
import ru.nsu.a.lyamin.snake_game.SnakeGame;

import java.util.*;

public class GameWindow
{
    private GUIFx guiFx;

    private final SnakeGame snakeGame;

    private SnakesProto.GameConfig gameConfig;

    private int pi;
    private GraphicsContext context;

    private TableView<Score> scores;

    private final int scoresWidth = 150;
    private final int windowWidth = 1000;
    private final int windowHeight = 700;

    private SnakesProto.NodeRole nodeRole;
    private Discoverer discoverer;

    private double cellWidth = 30;


    public GameWindow(SnakesProto.GameConfig _gameConfig, GUIFx _guiFx, Discoverer _discoverer, String name, SnakesProto.NodeRole _nodeRole, HostInfo hi)
    {
        guiFx = _guiFx;
        discoverer = _discoverer;
        nodeRole = _nodeRole;

        gameConfig = _gameConfig;

        snakeGame = new SnakeGame(gameConfig, this, nodeRole);

        MessageManager messageManager = snakeGame.getMessageManager();

        messageManager.sendJoin(hi, name);

        createWindow();
    }

    public GameWindow(SnakesProto.GameConfig _gameConfig, GUIFx _guiFx, Discoverer _discoverer, String name, SnakesProto.NodeRole _nodeRole)
    {
        guiFx = _guiFx;
        discoverer = _discoverer;
        nodeRole = _nodeRole;

        gameConfig = _gameConfig;

        snakeGame = new SnakeGame(gameConfig, this, nodeRole);

        MessageManager messageManager = snakeGame.getMessageManager();


        if((pi = messageManager.addMe(name, SnakesProto.NodeRole.MASTER,
                SnakesProto.PlayerType.HUMAN)) == -1)
        {
            System.out.println("Unable to create Snake");
            ErrorBox.display("Unable to create Snake");
        }

        discoverer.sendAnnouncementMsg(snakeGame, gameConfig);

        createWindow();

    }

    private void createWindow()
    {

        MessageManager messageManager = snakeGame.getMessageManager();

        cellWidth = (double)(windowWidth - scoresWidth)/snakeGame.getWidth();
        if(cellWidth > (double)windowHeight/snakeGame.getHeight())
        {
            cellWidth = (double)windowHeight/snakeGame.getHeight();
        }

        Canvas c = new Canvas(snakeGame.getWidth() * cellWidth, snakeGame.getHeight() * cellWidth);
        context = c.getGraphicsContext2D();

        Stage window = new Stage();

        VBox vbox = new VBox();
        Button becameViewer = new Button("Became Viewer");
        becameViewer.setFocusTraversable(false);

        Button exitButton = new Button("Exit");
        exitButton.setFocusTraversable(false);

        draw();
        createScores();

        vbox.getChildren().addAll(scores, becameViewer, exitButton);

        Pane p = new Pane(c);

        HBox hbox = new HBox(p, vbox);

        Scene scene = new Scene(hbox, windowWidth, cellWidth * snakeGame.getHeight());
        scene.setOnKeyPressed(keyEvent ->
        {
            if (keyEvent.getCode() == KeyCode.LEFT)
            {
                if(nodeRole != SnakesProto.NodeRole.MASTER)
                {
                    messageManager.sendSteer(pi, SnakesProto.Direction.LEFT);
                }
                snakeGame.changeSnakeDir(pi, SnakesProto.Direction.LEFT);
            }
            else if (keyEvent.getCode() == KeyCode.RIGHT)
            {
                if(nodeRole != SnakesProto.NodeRole.MASTER)
                {
                    messageManager.sendSteer(pi, SnakesProto.Direction.RIGHT);
                }

                snakeGame.changeSnakeDir(pi, SnakesProto.Direction.RIGHT);
            }
            else if (keyEvent.getCode() == KeyCode.UP)
            {
                if(nodeRole != SnakesProto.NodeRole.MASTER)
                {
                    messageManager.sendSteer(pi, SnakesProto.Direction.UP);
                }

                snakeGame.changeSnakeDir(pi, SnakesProto.Direction.UP);
            }
            else if (keyEvent.getCode() == KeyCode.DOWN)
            {
                if(nodeRole != SnakesProto.NodeRole.MASTER)
                {
                    messageManager.sendSteer(pi, SnakesProto.Direction.DOWN);
                }

                snakeGame.changeSnakeDir(pi, SnakesProto.Direction.DOWN);
            }
        });


        Timer timer = new Timer();
        timer.scheduleAtFixedRate(
        new TimerTask() {
            @Override
            public void run()
            {
                Platform.runLater( () ->
                {
                    synchronized (snakeGame)
                    {
                        snakeGame.moveSnakes();
                        draw();
                        updateScores();

                        if(nodeRole == SnakesProto.NodeRole.MASTER)
                        {
                            messageManager.sendState();
                        }
                    }
                });
            }
        },
        0,
        gameConfig.getStateDelayMs());

        window.setOnCloseRequest(windowEvent ->
        {
            timer.cancel();
            discoverer.stopSendAnnouncementMsg();
            messageManager.disableMessageManager();
        });


        window.setScene(scene);
        window.show();
    }

    public void repaint()
    {
        synchronized (snakeGame)
        {
            snakeGame.moveSnakes();
            draw();
            updateScores();
        }
    }


    private  void draw()
    {
        HashMap<Integer, Snake> snakes  = snakeGame.getSnakes();

        context.setFill(Color.BLACK);
        context.fillRect(0, 0, cellWidth * snakeGame.getWidth(), cellWidth * snakeGame.getHeight());


        for(Map.Entry<Integer, Snake> entry : snakes.entrySet())
        {

            ArrayList<Point> snakeBody = entry.getValue().getSnakeBody();
            for(int i = 0; i < snakeBody.size(); ++i)
            {
                int x = snakeBody.get(i).getX();
                int y = snakeBody.get(i).getY();
                if(i == 0)
                {
                    context.setFill(Color.GREEN);
                    context.fillRect(cellWidth * x, cellWidth * y, cellWidth , cellWidth);
                    continue;
                }
                context.setFill(Color.RED);
                context.fillRect(cellWidth * x, cellWidth * y, cellWidth , cellWidth);
            }
        }

        context.setFill(Color.YELLOW);
        for(Point p : snakeGame.getFood())
        {
            context.fillRect(cellWidth * p.getX(), cellWidth * p.getY(), cellWidth , cellWidth);
        }

    }

    private  void createScores()
    {
        TableColumn<Score, String> nameCloumn = new TableColumn<>("Name");
        nameCloumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCloumn.setMinWidth(100);

        TableColumn<Score, Integer> scoreColumn = new TableColumn<>("Score");
        scoreColumn.setCellValueFactory(new PropertyValueFactory<>("score"));
        scoreColumn.setSortType(TableColumn.SortType.DESCENDING);
        scoreColumn.setMinWidth(50);


        scores = new TableView<>();
        scores.getColumns().addAll(nameCloumn, scoreColumn);
        scores.setMaxWidth(windowWidth - snakeGame.getWidth() * cellWidth);
        scores.setMinWidth(windowWidth - snakeGame.getWidth() * cellWidth);
        scores.setEditable(false);
        scores.setFocusTraversable(false);
        scores.sort();
    }

    private  void updateScores()
    {
//        HashMap<Integer, Snake> snakes  = snakeGame.getSnakes();
        ObservableList<Score> scoresNew = FXCollections.observableArrayList();
        for(Map.Entry<Integer, SnakesProto.GamePlayer> entry : snakeGame.getPlayers().entrySet())
        {
            scoresNew.add(new Score(entry.getValue().getName(), entry.getValue().getScore()));
        }

        scores.setItems(scoresNew);

    }

    public void setPi(int pi)
    {
        this.pi = pi;
    }

    //    private  ObservableList<Score> getScores()
//    {
//        ObservableList<Score> scores = FXCollections.observableArrayList();
//
//        return scores;
//    }

}
