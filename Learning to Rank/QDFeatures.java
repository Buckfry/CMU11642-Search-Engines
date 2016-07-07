import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.HashMap;

/**
 * Created by haoming on 11/17/15.
 */
public class QDFeatures {

    //data
    public int qid;
    public int docid;//internal docid
    public String relevance;

    public String rawUrl;

    public List<String> queryWords;
    public HashMap<String, Integer> querySet;//qtf
    public double k_1;//bm25
    public double b;//bm25
    public double k_3;//bm25
    public double mu;//Indri
    public double lambda;//Indri


    public TermVector tvBody;
    public TermVector tvTitle;
    public TermVector tvUrl;
    public TermVector tvInlink;



    public double score;
    //Features
    public double[] features;
    /*
    public int spamScore;//f1
    public int urlDepth;//f2
    public int fromWiki;//f3
    public double pageRank;//f4
    public double bm25Body;//f5
    public double indriBody;//f6
    public double overlapBody;//f7
    public double bm25Title;//f8
    public double indriTitle;//f9
    public double overlapTitle;//f10
    public double bm25Url;//f11
    public double indriUrl;//f12
    public double overlapUrl;//f13
    public double bm25Inlink;//f14
    public double indriInlink;//f15
    public double overlapInlink;//f16
    public double custom1;//f17
    public double custom2;//f18
    */

    public QDFeatures (){}

    public QDFeatures (int qid, List<String> queryWords, int docid, double k_1, double b, double k_3, double mu, double lambda, String relavance) throws IOException {
        this.qid = qid;
        this.queryWords = queryWords;
        this.docid = docid;
        this.rawUrl = Idx.getAttribute("rawUrl", docid);
        this.k_1 = k_1;
        this.b = b;
        this.k_3 = k_3;
        this.mu = mu;
        this.lambda = lambda;
        this.relevance = relavance;

        int i;
        this.features = new double[18];
        for (i=0; i < 18; i++){
            this.features[i] = Double.NaN;
        }


        //compute QTF
        querySet = new HashMap<>(5);
        for (String word : queryWords){
            if (querySet.containsKey(word)){
                querySet.put(word, querySet.get(word)+1);
            }else{
                querySet.put(word,1);
            }
        }


        tvBody = new TermVector(docid, "body");
        tvInlink = new TermVector(docid, "inlink");
        tvTitle = new TermVector(docid, "title");
        tvUrl = new TermVector(docid, "url");



        if(!QryEval.featuresDisable.contains(7)) {
            features[6] = computeOverlap("body");//f7
        }
        if(!QryEval.featuresDisable.contains(10)) {
            features[9] = computeOverlap("title");//f10
        }
        if(!QryEval.featuresDisable.contains(13)) {
            features[12] = computeOverlap("url");//f13
        }
        if(!QryEval.featuresDisable.contains(16)) {
            features[15] = computeOverlap("inlink");//f16
        }

        if(!QryEval.featuresDisable.contains(1)) {
            features[0] = Integer.parseInt(Idx.getAttribute("score", docid));//f1
        }
        if(!QryEval.featuresDisable.contains(2)) {
            features[1] = computeUrlDepth();//f2
        }
        if(!QryEval.featuresDisable.contains(3)) {
            features[2] = isFromWiki();//f3
        }
        if(!QryEval.featuresDisable.contains(4)) {
            features[3] = readPageRank();//f4
        }
        if(!QryEval.featuresDisable.contains(5)) {
            features[4] = computeBm25("body");//f5
        }
        if(!QryEval.featuresDisable.contains(6)) {
            features[5] = computeIndri("body");//f6
        }
        if(!QryEval.featuresDisable.contains(8)) {
            features[7] = computeBm25("title");//f8
        }
        if(!QryEval.featuresDisable.contains(9)) {
            features[8] = computeIndri("title");//f9
        }
        if(!QryEval.featuresDisable.contains(11)) {
            features[10] = computeBm25("url");//f11
        }
        if(!QryEval.featuresDisable.contains(12)) {
            features[11] = computeIndri("url");//f12
        }
        if(!QryEval.featuresDisable.contains(14)) {
            features[13] = computeBm25("inlink");//f14
        }
        if(!QryEval.featuresDisable.contains(15)) {
            features[14] = computeIndri("inlink");//f15
        }
        if(!QryEval.featuresDisable.contains(17)) {
            features[16] = computeCustom1();//f17
        }
        if(!QryEval.featuresDisable.contains(18)) {
            features[17] = computeCustom2();//f18
        }


        // debug
        /*
        System.out.println("qid:" + qid + " Docid:" + docid);
        System.out.println("spamScore:" + features[0]);
        System.out.println("urlDepth:" + features[1]);
        System.out.println("fromWiki:" + features[2]);
        System.out.println("pageRank:" + features[3]);
        System.out.println("bm25Body:" + features[4]);
        System.out.println("indriBody:" + features[5]);
        System.out.println("overlapBody:" + features[6]);
        System.out.println("bm25Title:" + features[7]);
        System.out.println("indriTitle:" + features[8]);
        System.out.println("overlapTitle:" + features[9]);
        System.out.println("bm25Url:" + features[10]);
        System.out.println("indriUrl:" + features[11]);
        System.out.println("overlapUrl:" + features[12]);
        System.out.println("bm25Inlink:" + features[13]);
        System.out.println("indriInlink:" + features[14]);
        System.out.println("overlapInlink:" + features[15]);
        */



    }


    public double computeUrlDepth(){
        int count = 0;
        for (int i=0; i<rawUrl.length(); i++) {
            if (rawUrl.charAt(i) == '/'){
                count++;
            }
        }
        //System.out.println(count);
        return (double)count;
    }

    public int isFromWiki(){//f3
        if (rawUrl.contains("wikipedia.org")) {
            return 1;
        }else{
            return 0;
        }
    }

    public double readPageRank() {//f4
        if (QryEval.pageRank.containsKey(docid)) {
            return QryEval.pageRank.get(docid);
        } else {
            return Double.NaN;
        }
    }

    public double computeBm25(String field)  throws IOException {//f5
        TermVector termVector = null;
        switch (field){
            case "body":
                termVector = tvBody;
                break;
            case "inlink":
                termVector = tvInlink;
                break;
            case "title":
                termVector = tvTitle;
                break;
            case "url":
                termVector = tvUrl;
                break;
            default:
                System.out.println("Unknown field!" + field);
        }

        if (termVector.positionsLength() == 0){
            return Double.NaN;
        }

        int i;

        double score = 0;
        String stem = null;
        for (i=1; i < termVector.stemsLength(); i++){
            stem = termVector.stemString(i);
            if (querySet.containsKey(stem)){
                score += getScoreBM25(i, termVector, field) * (k_3+1)*querySet.get(stem)/(k_3+querySet.get(stem));
            }
        }
        return score;
    }

    public double getScoreBM25 (int stemId, TermVector termVector, String field) throws IOException {

            double tf = termVector.stemFreq(stemId);

            double docLength = termVector.positionsLength();
            double N = Idx.getNumDocs();//get doc count
            double avgDocLength = ((double)Idx.getSumOfFieldLengths(field))/(double)Idx.getDocCount(field);
            double df = termVector.stemDf(stemId);
            double rsj = 0;
            if (df < N/2){
                rsj = Math.log((N - df + 0.5) / (df + 0.5));
            }
            double tfweight = tf/(tf+k_1*(1-b+b*docLength/avgDocLength));
            return rsj*tfweight;
    }

    public double computeIndri(String field) throws IOException {//f6
        TermVector termVector = null;
        switch (field){
            case "body":
                termVector = tvBody;
                if (features[6] == 0d){
                    return 0d;
                }
                break;
            case "inlink":
                termVector = tvInlink;
                if (features[15] == 0d){
                    return 0d;
                }
                break;
            case "title":
                termVector = tvTitle;
                if (features[9] == 0d){
                    return 0d;
                }
                break;
            case "url":
                termVector = tvUrl;
                if (features[12] == 0d){
                    return 0d;
                }
                break;
            default:
                System.out.println("Unknown field!" + field);
        }
        if (termVector.positionsLength() == 0){
            return Double.NaN;
        }

        double score = 1d;
        for (String word : queryWords) {
            int stemId = termVector.indexOfStem(word);
            if ( stemId < 0) {
                score *= getIndriDefaultScore(word, field);
            }else {
                score *= getScoreIndri (stemId, termVector, field);
            }
        }
        score = Math.pow(score, 1d / queryWords.size());


        return score;
    }

    public double getIndriDefaultScore (String word, String field) throws IOException{

        double ctf = Idx.INDEXREADER.totalTermFreq(new Term(field, word));
        double tf = 0;
        double docLength = Idx.getFieldLength(field, docid);
        double cLength = (double)Idx.getSumOfFieldLengths(field);
        double pMLEc = ctf/cLength;
        double p = (1 - lambda) * (tf + mu * pMLEc) / (docLength + mu) + lambda * pMLEc;
        return p;
    }


    public double getScoreIndri (int stemId, TermVector termVector, String field) throws IOException {

        double ctf = termVector.totalStemFreq(stemId);
        double tf = termVector.stemFreq(stemId);
        double docLength = Idx.getFieldLength(field, docid);
        double cLength = (double)Idx.getSumOfFieldLengths(field);
        double pMLEc = ctf/cLength;
        double p = (1 - lambda) * (tf + mu * pMLEc) / (docLength + mu) + lambda * pMLEc;
        return p;
    }


    public double computeOverlap(String field) throws IOException{//f7

        TermVector termVector = null;
        switch (field){
            case "body":
                termVector = tvBody;
                break;
            case "inlink":
                termVector = tvInlink;
                break;
            case "title":
                termVector = tvTitle;
                break;
            case "url":
                termVector = tvUrl;
                break;
            default:
                System.out.println("Unknown field!" + field);
        }

        if (termVector.stemsLength() == 0){
            return Double.NaN;
        }


        String word;
        int count = 0;
        for (int i=1; i < termVector.stemsLength(); i++){
            word = termVector.stemString(i);
            if (querySet.containsKey(word)){
                count += querySet.get(word);
            }
        }
        double ratio = (double)count/(double)queryWords.size();

        if (ratio > 1){
            System.out.println("ERROR: overlap is > 1!");
        }

        //System.out.println("Overlap: " + qid + " " + docid + " " + field + " " + ratio);
        return ratio;
    }


    public double computeCustom1() {//f17 min(tf1, tf2, ...)
        TermVector termVector = tvBody;
        if (termVector.stemsLength() == 0){
            return Double.NaN;
        }

        int min = Integer.MAX_VALUE;

        for (String word : queryWords){
            int stemId = termVector.indexOfStem(word);
            int tf = 0;
            if ( stemId >= 0){
                tf = termVector.stemFreq(stemId);
            }
            if (tf < min){
                min = tf;
            }
        }

        return min;
    }
    public double computeCustom2() {//f18 sum(tf1, tf2, ...)
        TermVector termVector = tvBody;
        if (termVector.stemsLength() == 0){
            return Double.NaN;
        }

        int sum = 0;

        for (String word : queryWords){
            int stemId = termVector.indexOfStem(word);
            if ( stemId >= 0){
                sum += termVector.stemFreq(stemId);
            }
        }

        return sum;
    }


}
