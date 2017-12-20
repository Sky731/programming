package xyz.sky731.programming.lab2;

import ru.ifmo.se.pokemon.*;

public class Flygon extends Vibrava {
    public Flygon(String name) {
        super(name, 29);
        setStats(80, 100, 80, 80, 80, 100);
        setType(Type.DRAGON, Type.GROUND);

        Move[] moves = new Move[4];
        moves[0] = new DoubleTeam();
        moves[1] = new Confide();
        moves[2] = new BugBuzz();
        moves[3] = new DragonBreath();
        setMove(moves);
    }
}

class DragonBreath extends SpecialMove {
    DragonBreath() {
        super(Type.DRAGON, 60, 1);
    }

    @Override
    protected String describe() {
        return "DragonBreath";
    }
}