import java.util.Comparator;

/**
 * Created by haoming on 11/5/15.
 * Used for query expansion
 */
public class TermScore implements Comparator<TermScore>, Comparable<TermScore>{
    public String term;
    public Double score;
    public TermScore(String  term, double score){
        this.score = score;
        this.term = term;
    }

    public TermScore(){}

    public int compareTo(TermScore t){
        return t.score.compareTo(this.score);
    }


    public int compare(TermScore t1, TermScore t2){
        return Double.compare(t2.score, t1.score);
    }

}
