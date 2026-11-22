package Project.Roguelike;

public final class RogueStats {
    //region Constants
    public static final int MAX_HP = 100;
    public static final int POTION_HEAL = 40;
    public static final int MAX_DRINKS = 3;
    public static final int MAX_CARRIED = 3;
    public static final int START_POTIONS = 1;
    //endregion

    //region Variables
    private int hp = MAX_HP;
    private int carried = START_POTIONS;
    private int drinks;
    private int kills;
    //endregion

    //region Health
    public int hp() { return hp; }
    public boolean dead() { return hp <= 0; }

    public boolean damage(int amount) {
        hp = Math.max(0, hp - Math.max(0, amount));
        return hp == 0;
    }
    public int drink() {
        if (!canDrink()) return 0;
        int before = hp;
        hp = Math.min(MAX_HP, hp + POTION_HEAL);
        carried--;
        drinks++;
        return hp - before;
    }
    //endregion

    //region Potions
    public boolean canDrink() { return carried > 0 && drinks < MAX_DRINKS; }
    public boolean drinkLimitReached() { return drinks >= MAX_DRINKS; }
    public boolean addPotion() {
        if (carried >= MAX_CARRIED) return false;
        carried++;
        return true;
    }
    public int potionsLeft() { return Math.min(carried, MAX_DRINKS - drinks); }
    public int drinks() { return drinks; }
    //endregion

    //region Kills
    public void kill() { kills++; }
    public int kills() { return kills; }
    //endregion
}