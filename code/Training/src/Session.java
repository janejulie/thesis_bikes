import java.time.LocalDate;
import java.util.Date;
import java.util.HashMap;

public class Session {
    protected int minutes;
    protected Method method;
    protected HashMap<Range, Integer> distribution;
    protected LocalDate day;

    public Session(int min, Method method, HashMap<Range, Integer> distribution, LocalDate day) {
        this.minutes = min;
        this.method = method;
        this.distribution = distribution;
        this.day = day;
    }

    public int getMinutes() {
        return minutes;
    }

    public Method getMethod() {
        return method;
    }

    public HashMap<Range, Integer> getDistribution() {
        return distribution;
    }

    public String prettyString(){
        return  "Session mit Beschreibung";
    }

    public LocalDate getDay() {
        return day;
    }

    @Override
    public String toString() {
        return "Session{" +
                "minutes=" + minutes +
                ", method='" + method + '\'' +
                ", distribution=" + distribution +
                '}';
    }
}
