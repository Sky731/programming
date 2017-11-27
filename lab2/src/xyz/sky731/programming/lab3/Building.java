package xyz.sky731.programming.lab3;

public abstract class Building {
    private int cost = 0;

    public Building(int cost) {
        this.cost = cost;
    }

    public int getCost() {
        return cost;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o != null && o instanceof Building) {
            Building building = (Building) o;
            if (cost != building.cost) return false;
            return true;
        } else return false;
    }

    @Override
    public String toString() {
        return "Здание стоимостью " + cost + " сантиков";
    }

    @Override
    public int hashCode() {
        return cost;
    }
}
