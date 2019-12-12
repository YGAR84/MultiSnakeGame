package ru.nsu.a.lyamin.view;


import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import ru.nsu.a.lyamin.discoverer.Discoverer;
import ru.nsu.a.lyamin.message_decoder.SnakesProto;
import ru.nsu.a.lyamin.message_manager.HostInfo;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GUIFx extends Application
{

    private ConcurrentHashMap<HostInfo, SnakesProto.GameMessage.AnnouncementMsg> sessionInfoMap = new ConcurrentHashMap<>();
    private Discoverer discoverer;

    private String name;

    public static void GUIFx(String[] args)
    {
        launch(args);
    }

    @Override
    public void start(Stage stage)
    {
        stage.setTitle("Snake game");

        //new Discoverer();

        if(!LoginWindow.display())
        {
            return;
        }

        name = LoginWindow.getName();


        Thread t;
        try
        {
            discoverer = new Discoverer(sessionInfoMap);

            t = new Thread(discoverer);
            t.start();
        }
        catch (IOException e)
        {
            e.printStackTrace();
            return;
        }

        stage.setOnCloseRequest(actionEvent -> t.interrupt() );

        TableView<SessionInfo> tableView = createTableViewColumns();

        Button newGameButton = new Button("New game");
        newGameButton.setOnAction(actionEvent ->
        {
            if(NewGameWindow.display())
            {
                SnakesProto.GameConfig gameConfig = createGameConfig();

                new GameWindow(gameConfig, this, discoverer, name, SnakesProto.NodeRole.MASTER);
            }
        });

        Button connectButton = new Button("Connect");
        connectButton.setOnAction(actionEvent ->
        {
            ObservableList<SessionInfo> sessionSelected;
            sessionSelected  = tableView.getSelectionModel().getSelectedItems();


            SessionInfo si = sessionSelected.get(0);

            HostInfo hostInfo = new HostInfo(si.getIp(), si.getPort());

            new GameWindow(si.getGameConfig(), this, discoverer, name, SnakesProto.NodeRole.NORMAL, hostInfo);



            System.out.println(si.getIp() + " " + si.getPort());
        });

        Button refreshButton = new Button("Refresh");
        refreshButton.setOnAction(actionEvent ->
        {

            System.out.println("Refreshed");
            tableView.getItems().removeAll();
            tableView.setItems(getSessionsInfo());
        });



        HBox bottomMenu = new HBox(100);
        bottomMenu.setMinHeight(100);
        bottomMenu.getChildren().addAll(newGameButton, connectButton, refreshButton);
        bottomMenu.setAlignment(Pos.CENTER);

        VBox layout = new VBox();
        layout.getChildren().addAll(tableView, bottomMenu);
        layout.setMinWidth(250 + 50 + 100 + 50 + 50 + 75 + 75 + 75 + 75 + 170);

        Scene scene = new Scene(layout);

        stage.setScene(scene);

        stage.show();
    }

    private TableView<SessionInfo> createTableViewColumns()
    {
        TableView<SessionInfo> tableView;

        TableColumn<SessionInfo, InetAddress> ipColumn = new TableColumn<>("IP");
        ipColumn.setMaxWidth(250);
        ipColumn.setCellValueFactory(new PropertyValueFactory<>("ip"));


        TableColumn<SessionInfo, Integer> portColumn = new TableColumn<>("Port");
        portColumn.setMaxWidth(50);
        portColumn.setCellValueFactory(new PropertyValueFactory<>("port"));

        TableColumn<SessionInfo, Integer> widthColumn = new TableColumn<>("Width");
        widthColumn.setMaxWidth(50);
        widthColumn.setCellValueFactory(new PropertyValueFactory<>("width"));

        TableColumn<SessionInfo, Integer> heightColumn = new TableColumn<>("Height");
        heightColumn.setMaxWidth(50);
        heightColumn.setCellValueFactory(new PropertyValueFactory<>("height"));

        TableColumn<SessionInfo, Integer> baseFoodColumn = new TableColumn<>("Base food");
        baseFoodColumn.setMaxWidth(75);
        baseFoodColumn.setCellValueFactory(new PropertyValueFactory<>("baseFood"));

        TableColumn<SessionInfo, Double> foodMultiplyerColumn = new TableColumn<>("Food multi");
        foodMultiplyerColumn.setMaxWidth(75);
        foodMultiplyerColumn.setCellValueFactory(new PropertyValueFactory<>("foodMultiplyer"));

        TableColumn<SessionInfo, Double> foodDropChanceColumn = new TableColumn<>("Drop chance");
        foodDropChanceColumn.setMaxWidth(75);
        foodDropChanceColumn.setCellValueFactory(new PropertyValueFactory<>("foodDropChance"));

        TableColumn<SessionInfo, Integer> numOfPlayersColumn = new TableColumn<>("Num of players");
        numOfPlayersColumn.setMaxWidth(75);
        numOfPlayersColumn.setCellValueFactory(new PropertyValueFactory<>("numOfPlayers"));

        TableColumn<SessionInfo, Boolean> canJoinColumn = new TableColumn<>("Can join");
        canJoinColumn.setMaxWidth(170);
        canJoinColumn.setCellValueFactory(new PropertyValueFactory<>("canJoin"));
        System.out.println(canJoinColumn.getWidth());
        canJoinColumn.setSortType(TableColumn.SortType.DESCENDING);

        tableView = new TableView<>();
        tableView.setItems(getSessionsInfo());
        tableView.getColumns().addAll(ipColumn, portColumn, widthColumn, heightColumn, baseFoodColumn, foodMultiplyerColumn, foodDropChanceColumn, numOfPlayersColumn, canJoinColumn);

        return tableView;
    }

    private ObservableList<SessionInfo> getSessionsInfo()
    {
        ObservableList<SessionInfo> sessionsInfo = FXCollections.observableArrayList();

        for(Map.Entry<HostInfo, SnakesProto.GameMessage.AnnouncementMsg> entry : sessionInfoMap.entrySet())
        {
            SnakesProto.GameConfig gameConfig = entry.getValue().getConfig();
            sessionsInfo.add(new SessionInfo(
                    entry.getKey().getIp(), entry.getKey().getPort(), gameConfig.getWidth(),
                    gameConfig.getHeight(), gameConfig.getFoodStatic(), gameConfig.getFoodPerPlayer(),
                    gameConfig.getDeadFoodProb(), entry.getValue().getPlayers().getPlayersCount(), entry.getValue().getCanJoin(),
                    entry.getValue().getConfig())
            );
        }

        return sessionsInfo;
    }

    private SnakesProto.GameConfig createGameConfig()
    {
        return SnakesProto.GameConfig.newBuilder()
                .setWidth(NewGameWindow.getWidth())
                .setHeight(NewGameWindow.getHeight())
                .setFoodStatic(NewGameWindow.getBaseFood())
                .setFoodPerPlayer(NewGameWindow.getFoodMultiplyer())
                .setStateDelayMs(NewGameWindow.getStateDelay())
                .setDeadFoodProb(NewGameWindow.getFoodDropChance())
                .setPingDelayMs(NewGameWindow.getPingDelay())
                .setNodeTimeoutMs(NewGameWindow.getNodeTimeout())
                .build();
    }

    public void startGame()
    {
        
    }
}


//        HBox topMenu = new HBox();
//        Button buttonA = new Button("Button A");
//        Button buttonB = new Button("Button B");
//        Button buttonC = new Button("Button C");
//
//        topMenu.getChildren().addAll(buttonA, buttonB, buttonC);
//
//
//        VBox leftMenu = new VBox();
//        Button buttonD = new Button("Button D");
//        Button buttonE = new Button("Button E");
//        Button buttonF = new Button("Button F");
//
//        leftMenu.getChildren().addAll(buttonD, buttonE, buttonF);
//
//        BorderPane borderPane = new BorderPane();
//        borderPane.setTop(topMenu);
//        borderPane.setLeft(leftMenu);
//
//        Scene scene = new Scene(borderPane, 300, 300);
//        window.setScene(scene);


//    button = new Button("click me");
//        button.setOnAction(actionEvent -> closeProgramm());
//
//
//        window.setOnCloseRequest(actionEvent ->
//    {
//        actionEvent.consume();
//        closeProgramm();
//    });
//
//
//    StackPane layout = new StackPane();
//        layout.getChildren().add(button);
//    Scene scene = new Scene(layout, 300, 300);
//        window.setScene(scene);
//        window.show();
//
//}
//
//    private void closeProgramm()
//    {
//        boolean result = ConfirmBox.display("Clsoe", "Are you sure?");
//        System.out.println(result);
//        if(result)
//            window.close();
//    }
//       window = stage;
//
//               Label label1 = new Label("First scene");
//
//               Button button1 = new Button("Go to scene 2");
//               button1.setOnAction(actionEvent -> window.setScene(scene2));
//
//               VBox layout1 = new VBox(20);
//
//               layout1.getChildren().addAll(label1, button1);
//               scene1 = new Scene(layout1, 300, 300);
//
//
//               Button button2 = new Button("Go to scene 1");
//               button2.setOnAction(actionEvent -> window.setScene(scene1));
//
//               StackPane layout2 = new StackPane();
//               layout2.getChildren().add(button2);
//               scene2 = new Scene(layout2, 200, 200);
//
//               window.setScene(scene1);
//
//               window.setTitle("LA");
//               window.show();

