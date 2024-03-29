package lostinberlin;

public enum TestEnum {
    WHITE('white', 'White is mix of all colors'),
    BLACK('black', 'Black is no colors'),
    RED('red', 'Red is the color of blood')
 
    final String id;
    final String desc;
    static final Map map;
    static {
        map = [:]
        values().each{ color -> 
            map.put(color.toString(), color)
        }
 
    }
 
    public static valueOf(String id) {
        map[id]
    }

    private TestEnum(String id, String desc) {
        this.id = id;
        this.desc = desc;
    }
}
