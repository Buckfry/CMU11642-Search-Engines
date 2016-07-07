/**
 * Created by haoming on 11/5/15.
 * Used for query expansion
 */
public class DocScore {
    public int docid;//internal
    public double score;//Indri score
    public DocScore(int docid, double score){
        this.docid = docid;
        this.score = score;
    }
}
