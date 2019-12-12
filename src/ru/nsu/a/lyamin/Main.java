//package ru.nsu.a.lyamin;
//
//import ru.nsu.a.lyamin.snake_game.*;
//
//import java.net.InetAddress;
//import java.net.UnknownHostException;
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.Map;
//import java.util.Scanner;
//
//public class Main
//{
//
//
//
//    public static void main(String[] args) throws UnknownHostException
//    {
//
//        //new GUIFx(args);
//
//        FoodFunc a = (x) -> (2 * x + 3);
//        SnakeGame snakeGame = new SnakeGame(10, 10, a, 2, 0);
//
//        PlayerInfo pi = new PlayerInfo(InetAddress.getLocalHost(), 8000, "Allah");
//        System.out.println(snakeGame.addPlayer(pi));
//
//        PlayerInfo pi2 = new PlayerInfo(InetAddress.getLocalHost(), 8000, "Allaaa");
//        System.out.println(snakeGame.addPlayer(pi2));
//
//        PlayerInfo pi3 = new PlayerInfo(InetAddress.getLocalHost(), 8000, "Antoha");
//        System.out.println(snakeGame.addPlayer(pi3));
//
//        Scanner scanner = new Scanner(System.in);
//        while(true)
//        {
//            String move = scanner.nextLine();
//            switch(move)
//            {
//                case "w" :
//                {
//                    snakeGame.moveSnake(pi, Movement.MOVE_UP);
//                    break;
//                }
//                case "d" :
//                {
//                    snakeGame.moveSnake(pi, Movement.MOVE_RIGHT);
//                    break;
//                }
//                case "s" :
//                {
//                    snakeGame.moveSnake(pi, Movement.MOVE_DOWN);
//                    break;
//                }
//                case "a" :
//                {
//                    snakeGame.moveSnake(pi, Movement.MOVE_LEFT);
//                    break;
//                }
//            }
//
//
//            FieldType[][] field = snakeGame.getField();
//            HashMap<PlayerInfo, Snake> snakes  = snakeGame.getSnakes();
//
//            char[][] drawField = new char[snakeGame.getWidth()][snakeGame.getLength()];
//
//            for(int i = 0; i < field.length; ++i)
//            {
//                for(int j = 0; j < field[0].length; ++j)
//                {
//                    switch(field[i][j])
//                    {
//                        case FIELD_FOOD:
//                        {
//                            drawField[i][j] = '.';
//                            break;
//                        }
//                        case FIELD_EMPTY:
//                        {
//                            drawField[i][j] = ' ';
//                            break;
//                        }
//                    }
//                }
//            }
//
//            for(Map.Entry<PlayerInfo, Snake> entry : snakes.entrySet())
//            {
//                ArrayList<Point> snakeBody = entry.getValue().getSnakeBody();
//                for(int i = 0; i < snakeBody.size(); ++i)
//                {
//                    if(i == 0)
//                    {
//                        drawField[snakeBody.get(i).getX()][snakeBody.get(i).getY()] = 'h';
//                        continue;
//                    }
//                    drawField[snakeBody.get(i).getX()][snakeBody.get(i).getY()] = 's';
////                    System.out.println("x:" + p.getX() + "; y:" + p.getY());
//                }
//            }
//
//
//            System.out.println("--------------------------");
//            for(int j = 0; j < drawField[0].length; ++j)
//            {
//                System.out.print("|");
//                for(int i = 0; i < drawField.length; ++i)
//                {
//                    System.out.printf("%c", drawField[i][j]);
//                }
//                System.out.println("|");
//            }
//            System.out.println("--------------------------");
//
//        }
//    }
//
//
//
//    private void printField(char[][] drawField)
//    {
//        for(int i = 0; i < drawField.length; ++i)
//        {
//            for(int j = 0; j < drawField[0].length; ++j)
//            {
//                System.out.printf("|%c|", drawField[i][j]);
//            }
//            System.out.println();
//        }
//    }
//}
