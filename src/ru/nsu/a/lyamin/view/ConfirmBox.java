//package ru.nsu.a.lyamin.view;
//
//import javafx.geometry.Pos;
//import javafx.scene.Scene;
//import javafx.scene.control.Button;
//import javafx.scene.control.Label;
//import javafx.scene.layout.VBox;
//import javafx.stage.Modality;
//import javafx.stage.Stage;
//
//public class ConfirmBox
//{
//
//    static boolean answer;
//
//
//    public static boolean display(String title, String msg)
//    {
//        Stage window = new Stage();
//        window.initModality(Modality.APPLICATION_MODAL);
//        window.setTitle(title);
//        Label label = new Label(msg);
//
//
//        Button yesButton = new Button("Yes");
//        Button noButton = new Button("No");
//
//        yesButton.setOnAction(actionEvent -> {
//            answer =  true;
//            window.close();
//        });
//
//        noButton.setOnAction(actionEvent -> {
//            answer = false;
//            window.close();
//        });
//
//        VBox layout = new VBox(10);
//        layout.getChildren().addAll(label, yesButton, noButton);
//        layout.setAlignment(Pos.CENTER);
//
//        Scene scene = new Scene(layout);
//        window.setScene(scene);
//
//        window.showAndWait();
//
//
//        return answer;
//    }
//}
