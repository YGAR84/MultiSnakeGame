package ru.nsu.a.lyamin.snake_game;

import ru.nsu.a.lyamin.message_decoder.SnakesProto;

import java.util.ArrayList;

public class Snake
{
    private int fieldHeight;
    private int fieldWidth;
    private ArrayList<Point> snakeBody;
    private boolean hasEaten = false;
    private SnakesProto.Direction prevMovement;
    private int playerId;

    private SnakesProto.GameState.Snake.SnakeState snakeState;

    public Snake(SnakesProto.GameState.Snake _snakeMessage,
                 SnakesProto.GameConfig _gameConfig)
    {

        snakeBody = new ArrayList<>();

        fieldWidth = _gameConfig.getWidth();
        fieldHeight = _gameConfig.getHeight();

        decodeSnakeBodyFromCoords(_snakeMessage);

        snakeState = _snakeMessage.getState();
        prevMovement = _snakeMessage.getHeadDirection();

        playerId = _snakeMessage.getPlayerId();





    }

    public Snake(ArrayList<Point> _snakeBody, int _fieldLength, int _fieldWidth, int _playerId,
                 SnakesProto.GameState.Snake.SnakeState _snakeState)
    {
        snakeBody = _snakeBody;
        fieldHeight = _fieldLength;
        fieldWidth = _fieldWidth;
        playerId = _playerId;
        snakeState = _snakeState;

        prevMovement = getPrevMovementFromStart();
    }

    private SnakesProto.Direction getPrevMovementFromStart()
    {
        if(snakeBody.size() < 2) return SnakesProto.Direction.RIGHT;

        Point p1 = snakeBody.get(0);
        Point p2 = snakeBody.get(1);

        int diffX = p1.getX() - p2.getX();
        int diffY = p1.getY() - p2.getY();

        if(diffX != 0)
        {
            return (diffX > 0) ? SnakesProto.Direction.RIGHT : SnakesProto.Direction.LEFT;
        }

        if(diffY != 0)
        {
            return (diffY > 0) ? SnakesProto.Direction.DOWN : SnakesProto.Direction.UP;
        }

        return SnakesProto.Direction.UP;
    }

    void increaseSnake()
    {
        hasEaten = true;
    }


    boolean canMove(SnakesProto.Direction m)
    {
        SnakesProto.Direction antiMove;
        switch (prevMovement)
        {
            case UP:
            {
                antiMove = SnakesProto.Direction.DOWN;
                break;
            }
            case RIGHT:
            {
                antiMove = SnakesProto.Direction.LEFT;
                break;
            }
            case DOWN:
            {
                antiMove = SnakesProto.Direction.UP;
                break;
            }
            case LEFT:
            {
                antiMove = SnakesProto.Direction.RIGHT;
                break;
            }
            default: antiMove = SnakesProto.Direction.UP;
        }

        return m != antiMove;
    }

    public void setSnakeState(SnakesProto.GameState.Snake.SnakeState snakeState)
    {

        this.snakeState = snakeState;
    }

    void moveSnake(SnakesProto.Direction move)
    {
        if(hasEaten)
        {
            snakeBody.add(new Point(snakeBody.get(snakeBody.size() - 1)));
        }

        for(int i = snakeBody.size() - ((hasEaten) ? 1 : 0) - 1 ; i > 0; --i)
        {
            snakeBody.get(i).setValues(snakeBody.get(i - 1));
        }

        switch (move)
        {
            case UP:   { snakeBody.get(0).setY(snakeBody.get(0).getY() - 1); break; }
            case DOWN: { snakeBody.get(0).setY(snakeBody.get(0).getY() + 1); break; }
            case LEFT: { snakeBody.get(0).setX(snakeBody.get(0).getX() - 1); break; }
            case RIGHT:{ snakeBody.get(0).setX(snakeBody.get(0).getX() + 1); break; }
        }

        normalizeSnake();
        prevMovement = move;
        hasEaten = false;
    }

    private void normalizeSnake()
    {
        Point p;
        for (Point aSnake : snakeBody)
        {
            p = aSnake;

            int sX = p.getX();
            int sY = p.getY();

            sX %= fieldWidth;
            while (sX < 0) sX += fieldWidth;

            sY %= fieldHeight;
            while (sY < 0) sY += fieldHeight;

            p.setX(sX);
            p.setY(sY);
        }
    }

    public Point getHead()
    {
        return snakeBody.get(0);
    }

    public ArrayList<Point> getSnakeBody()
    {
        return snakeBody;
    }

    public int getSnakeSize()
    {
        return snakeBody.size();
    }

    public SnakesProto.Direction getPrevMovement()
    {
        return prevMovement;
    }

    public SnakesProto.GameState.Snake getBufferedSnake()
    {
        SnakesProto.GameState.Snake.Builder snakeBuilder = SnakesProto.GameState.Snake.newBuilder();

        snakeBuilder.setPlayerId(playerId);
        snakeBuilder.setState(snakeState);
        snakeBuilder.setHeadDirection(prevMovement);

        Point head = getHead();

        snakeBuilder.addPoints(SnakesProto.GameState.Coord.newBuilder()
                .setX(head.getX())
                .setY(head.getY())
        );


        Point lastPoint = getHead();

        for (int i = 1; i < snakeBody.size() - 1; ++i)
        {
            Point p1 = snakeBody.get(i - 1);
            Point p2 = snakeBody.get(i + 1);

            int xShift = p1.getX() - p2.getX();

            if (Math.abs(xShift) == 1)
            {
                snakeBuilder.addPoints(SnakesProto.GameState.Coord.newBuilder()
                        .setX(snakeBody.get(i).getX() - lastPoint.getX())
                        .setY(snakeBody.get(i).getY() - lastPoint.getY())
                );
                lastPoint = snakeBody.get(i);
            }
        }

        snakeBuilder.addPoints(SnakesProto.GameState.Coord.newBuilder()
                .setX(snakeBody.get(snakeBody.size() - 1).getX() - lastPoint.getX())
                .setY(snakeBody.get(snakeBody.size() - 1).getY() - lastPoint.getY())
        );


        return snakeBuilder.build();
    }

    public void loadSnake(SnakesProto.GameState.Snake _snakeMessage)
    {
        snakeBody.clear();

        decodeSnakeBodyFromCoords(_snakeMessage);

        prevMovement = _snakeMessage.getHeadDirection();

    }


    public int getPlayerId()
    {
        return playerId;
    }

    private void decodeSnakeBodyFromCoords(SnakesProto.GameState.Snake _snakeMessage)
    {
        SnakesProto.GameState.Coord head = _snakeMessage.getPointsList().get(0);

        snakeBody.add(0, new Point(head.getX(), head.getY()));

        for(int i = 1; i < _snakeMessage.getPointsCount(); ++i)
        {
            Point prevPoint = snakeBody.get(snakeBody.size() - 1);
            SnakesProto.GameState.Coord shift = _snakeMessage.getPoints(i);

            int yCoef = 0;
            int xCoef = 0;

            int numOfPoints = 0;

            if(shift.getX() == 0)
            {
                if(shift.getY() == 0)
                {
                    System.out.println("SOME DECODING SNAKE ERROR: shift point with coord (0, 0)");
                }
                else if(shift.getY() > 0)
                {
                    numOfPoints = shift.getY();
                    yCoef = 1;
                }
                else
                {
                    numOfPoints = -shift.getY();
                    yCoef = -1;
                }
            }
            else if(shift.getX() > 0)
            {
                numOfPoints = shift.getX();
                xCoef = 1;
            }
            else
            {
                numOfPoints = -shift.getX();
                xCoef = -1;
            }

            for(int j = 0; j < numOfPoints; ++j)
            {
                snakeBody.add(new Point(prevPoint.getX() + (j + 1) * xCoef,
                        prevPoint.getY() + (j + 1) * yCoef));
            }

        }

        normalizeSnake();

    }

}
