package lostinberlin;

public enum TestEnum {
    WHITE('white', 'White is mix of all colors'),
    BLACK('black', 'Black is no colors'),
    RED('red', 'Red is the color of blood')
 
    final String id;
    final String desc;

    private TestEnum(String id, String desc) {
        this.id = id;
        this.desc = desc;
    }
    
    public static valueOf(String s) {
        return super.valueOf(s)
    }
}
