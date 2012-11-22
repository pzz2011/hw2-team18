package edu.cmu.lti.oaqa.openqa.test.team18.passage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.uima.analysis_engine.AnalysisEngineProcessException;

import edu.cmu.lti.oaqa.framework.data.PassageCandidate;
import edu.cmu.lti.oaqa.openqa.hello.passage.KeytermWindowScorer;
import edu.cmu.lti.oaqa.openqa.hello.passage.KeytermWindowScorerProduct;
import edu.cmu.lti.oaqa.openqa.hello.passage.KeytermWindowScorerSum;
import edu.cmu.lti.oaqa.openqa.hello.passage.PassageCandidateFinder;
import edu.cmu.lti.oaqa.openqa.test.team18.passage.IBMstrategy.PassageSpan;

//import edu.cmu.lti.oaqa.openqa.hello.passage.PassageCandidateFinder.PassageSpan;

public class IBMstrategy {
  private String text;

  private String docId;

  private int textSize; // values for the entire text

  private int totalMatches;

  private int totalKeyterms;

  private KeytermWindowScorer scorer;

  public IBMstrategy(String docId, String text, KeytermWindowScorer scorer) {
    super();
    this.text = text;
    this.docId = docId;
    this.textSize = text.length();
    this.scorer = scorer;
  }

  public List<PassageCandidate> extractPassages(String[] keyterms) {
    List<List<PassageSpan>> matchingSpans = new ArrayList<List<PassageSpan>>();
    List<PassageSpan> matchedSpans = new ArrayList<PassageSpan>();

    // Find all keyterm matches.
    for (String keyterm : keyterms) {
      Pattern p = Pattern.compile(keyterm);
      Matcher m = p.matcher(text);
      while (m.find()) {
        PassageSpan match = new PassageSpan(m.start(), m.end());
        matchedSpans.add(match);
        totalMatches++;
      }
      if (!matchedSpans.isEmpty()) {
        matchingSpans.add(matchedSpans);
        totalKeyterms++;
      }
    }

    // create set of left edges and right edges which define possible windows.
    List<Integer> leftEdges = new ArrayList<Integer>();
    List<Integer> rightEdges = new ArrayList<Integer>();
    List<PassageSpan> pspan = getPassageSentences();
//    Iterator<PassageSpan> it = pspan.iterator();
//    while (it.hasNext()){
//      PassageSpan a = it.next();
//      System.out.println("wo shi span de jieguo:"+a.getBegin()+" "+a.getEnd());
//    }
//    
    for (PassageSpan ps : pspan) {
      Integer leftEdge = ps.begin;
      Integer rightEdge = ps.end;
      if (!leftEdges.contains(leftEdge))
        leftEdges.add(leftEdge);
      if (!rightEdges.contains(rightEdge))
        rightEdges.add(rightEdge);
    }

    // For every possible window, calculate keyterms found, matches found; score window, and create
    // passage candidate.
    List<PassageCandidate> result = new ArrayList<PassageCandidate>();
    for (Integer begin : leftEdges) {
      for (Integer end : rightEdges) {
        if (end <= begin)
          continue;
        // This code runs for each window.
        int keytermsFound = 0;
        int matchesFound = 0;
        for (List<PassageSpan> keytermMatches : matchingSpans) {
          boolean thisKeytermFound = false;
          for (PassageSpan keytermMatch : keytermMatches) {
            if (keytermMatch.containedIn(begin, end)) {
              matchesFound++;
              thisKeytermFound = true;
            }
          }
          if (thisKeytermFound)
            keytermsFound++;
        }
        double score = scorer.scoreWindow(begin, end, matchesFound, totalMatches, keytermsFound,
                totalKeyterms, textSize);
        PassageCandidate window = null;
        if (score >= 0.5) {
          try {
            window = new PassageCandidate(docId, begin, end, (float) score, null);

          } catch (AnalysisEngineProcessException e) {
            e.printStackTrace();
          }
          result.add(window);
        }
      }
    }

    // Sort the result in order of decreasing score.
    // Collections.sort ( result , new PassageCandidateComparator() );
    return result;

  }

  private class PassageCandidateComparator implements Comparator {
    // Ranks by score, decreasing.
    public int compare(Object o1, Object o2) {
      PassageCandidate s1 = (PassageCandidate) o1;
      PassageCandidate s2 = (PassageCandidate) o2;
      if (s1.getProbability() < s2.getProbability()) {
        return 1;
      } else if (s1.getProbability() > s2.getProbability()) {
        return -1;
      }
      return 0;
    }
  }

  class PassageSpan {
    private int begin, end;

    public int getBegin() {
      return begin;
    }

    public void setBegin(int begin) {
      this.begin = begin;
    }

    public int getEnd() {
      return end;
    }

    public void setEnd(int end) {
      this.end = end;
    }

    public PassageSpan(int begin, int end) {
      this.begin = begin;
      this.end = end;
    }

    public boolean containedIn(int begin, int end) {
      if (begin <= this.begin && end >= this.end) {
        return true;
      } else {
        return false;
      }
    }
  }

  public static void main(String[] args) {
    PassageCandidateFinder passageFinder1 = new PassageCandidateFinder("1",
            "The quick brown fox jumped over the quick brown fox.",
            new KeytermWindowScorerProduct());
    PassageCandidateFinder passageFinder2 = new PassageCandidateFinder("1",
            "The quick brown fox jumped over the quick brown fox.", new KeytermWindowScorerSum());
    String[] keyterms = { "quick", "jumped" };
    List<PassageCandidate> windows1 = passageFinder1.extractPassages(keyterms);
    System.out.println("Windows (product scoring): " + windows1);
    List<PassageCandidate> windows2 = passageFinder2.extractPassages(keyterms);
    System.out.println("Windows (sum scoring): " + windows2);
  }

  public List<PassageSpan> getPassageSentences() {
    List<PassageSpan> span = new ArrayList<PassageSpan>();
    int i = 0, j = 1;
    // int s=0, e=0;
   // int spanNumber = 0;
    while (j < textSize-2) {
      if (text.charAt(j) == '.' && text.charAt(j+2) >= 'A' && text.charAt(j+2) <= 'Z') {
        PassageSpan a = new PassageSpan(i, j);
        span.add(a);
        i = j;
        //spanNumber++;
      }
      j++;
    }
    PassageSpan a = new PassageSpan(i, textSize-1);
    span.add(a);
    // i = 0;
    // j = 1;
    // while (j < spanNumber) {
    // PassageSpan a = new PassageSpan(span.get(i).begin, span.get(j).end);
    // span.add(a);
    // i++;
    // j++;
    // }
    // i = 0;
    // j = 2;
    // while (j < spanNumber) {
    // PassageSpan a = new PassageSpan(span.get(i).begin, span.get(j).end);
    // span.add(a);
    // i++;
    // j++;
    // }

    return span;
  }
}