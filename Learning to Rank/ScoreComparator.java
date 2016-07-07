import java.util.Comparator;

public class ScoreComparator implements Comparator<QDFeatures> {
    @Override
    public int compare(QDFeatures o1, QDFeatures o2) {
        return Double.compare(o2.score, o1.score);
    }
}