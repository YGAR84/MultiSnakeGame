package ru.nsu.a.lyamin.view;

import javax.swing.*;
import java.awt.*;

public class GUI extends JFrame
{
    private int spectatorAreaWidth = 900;
    private int settingsAreaWidth = 400;
    private int windowAreaHeight = 700;

    private JPanel spectatorField = new JPanel();
    private JPanel settingsField = new JPanel();

    public GUI()
    {
        super("Snake");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        setBounds(10, 10, spectatorAreaWidth + settingsAreaWidth, windowAreaHeight);

        Container mainContainer = this.getContentPane();
        mainContainer.setLayout(new GridBagLayout());

        GridBagConstraints mainConstraints = new GridBagConstraints();
        mainConstraints.anchor = GridBagConstraints.LINE_START;
        mainConstraints.gridheight = 1;
        mainConstraints.gridwidth = 2;
        mainConstraints.gridx = 0;
        mainConstraints.gridy = 0;
        mainConstraints.weightx = 1.0;
        mainConstraints.weighty = 0.0;
        mainConstraints.fill = GridBagConstraints.WEST;

        mainContainer.add(spectatorField, mainConstraints);

        mainConstraints.anchor = GridBagConstraints.LINE_END;
        mainConstraints.fill = GridBagConstraints.EAST;
        mainConstraints.gridx = 1;
        mainConstraints.gridy = 0;
        mainConstraints.weighty = 0.0;
        mainConstraints.weightx = 0.5;
        mainContainer.add(settingsField, mainConstraints);

        spectatorField.setSize(spectatorAreaWidth, windowAreaHeight);
        settingsField.setSize(settingsAreaWidth, windowAreaHeight);
        spectatorField.setLayout(new GridLayout(20, 20));

        for(int i = 0; i < 20; ++i)
        {
            for(int j = 0; j < 20; ++j)
            {
                JLabel jl = new JLabel("a");
                jl.setSize(spectatorAreaWidth/20, windowAreaHeight/20);
                spectatorField.add(jl);
            }
        }

        settingsField.setLayout(new BorderLayout());
        settingsField.add(new JLabel("allalala"), BorderLayout.NORTH);
        settingsField.add(new JButton("asdfdgdsdf"), BorderLayout.SOUTH);

        this.setVisible(true);
    }
}
