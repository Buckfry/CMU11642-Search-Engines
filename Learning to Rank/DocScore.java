import java.util.Comparator;

/**
 * Created by haoming on 11/5/15.
 * Used for query expansion
 */
public class DocScore implements Comparator<DocScore>, Comparable<DocScore>{
    public int docid;
    public Double score;
    public DocScore(int docid, double score){
        this.score = score;
        this.docid = docid;
    }

    public DocScore(){}

    public int compareTo(DocScore t){
        return t.score.compareTo(this.score);
    }


    public int compare(DocScore t1, DocScore t2){
        return Double.compare(t2.score, t1.score);
    }

}