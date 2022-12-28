package org.com.gemini;

import gherkin.util.FixJava;
import io.cucumber.gherkin.GherkinParser;
import io.cucumber.messages.types.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.Exception;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.cucumber.messages.types.SourceMediaType.TEXT_X_CUCUMBER_GHERKIN_PLAIN;

public class GherkinToJsonByGitLink {

    static JSONArray ScenarioData = new JSONArray();

    public static String getApiUrl(String gitUrl) {
        String apiUrl = "";
       try{
           Pattern p = Pattern.compile("https://github.com/(.+)/(.+)");
           Matcher m = p.matcher(gitUrl);
           if (m.find()) {
               String username = m.group(1);
               String repoName = m.group(2);
               apiUrl = "https://api.github.com/repos/" + username + "/" + repoName + "/contents/src/main/resources/features";
           }
       }catch (Exception e){
           e.printStackTrace();
       }
        return apiUrl;
    }

    public static List<String> GetAllFeatureFileUrlsFromGit(String ApiUrl){
        List<String> featureFiles = new ArrayList<>();
       try{
           URL url = new URL(ApiUrl);
           HttpURLConnection con = (HttpURLConnection) url.openConnection();
           con.setRequestMethod("GET");
           BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
           String inputLine;
           StringBuilder content = new StringBuilder();
           while ((inputLine = in.readLine()) != null) {
               content.append(inputLine);
           }
           in.close();
           JSONArray response = new JSONArray(content.toString());
           for (int i = 0; i < response.length(); i++) {
               if(response.getJSONObject(i).getString("download_url").contains(".feature")) {
                   featureFiles.add(response.getJSONObject(i).getString("download_url"));
               }
           }
       }catch (Exception e){
           e.printStackTrace();
       }
        return featureFiles;
    }

    public static Feature GetFeatureFileFromGit(String GitDUrl){
    try{
        URL Url = new URL(GitDUrl);
        HttpURLConnection Http = (HttpURLConnection) Url.openConnection();
        Map<String, List<String>> Header = Http.getHeaderFields();
        for (String header : Header.get(null)) {
            if (header.contains(" 302 ") || header.contains(" 301 ")) {
                GitDUrl = Header.get("Location").get(0);
                Url = new URL(GitDUrl);
                Http = (HttpURLConnection) Url.openConnection();
                Header = Http.getHeaderFields();
            }
        }
        InputStream Stream = Http.getInputStream();
        String gherkin = FixJava.readReader(new InputStreamReader(Stream, StandardCharsets.UTF_8));
        Envelope envelope = Envelope.of(new Source("Features.feature", gherkin, TEXT_X_CUCUMBER_GHERKIN_PLAIN));
        GherkinDocument gherkinDocument = GherkinParser.builder().includeSource(false).includePickles(false).build().parse(envelope).findFirst().flatMap(Envelope::getGherkinDocument).get();
        return gherkinDocument.getFeature().get();
    }catch (Exception e){
        e.printStackTrace();
    }
    return null;
    }

    public static void ConvertFeatureJson(Feature features) {
      try{
          JSONObject currScenarioData = new JSONObject();
          JSONArray Steps = new JSONArray();
          JSONArray tags = new JSONArray();
          JSONObject Examples = new JSONObject();
          JSONArray ExamplesHeaders = new JSONArray();
          JSONArray ExamplesData = new JSONArray();
          for (FeatureChild iterator : features.getChildren()) {
              // Tags Name
              for (Tag featureChild : iterator.getScenario().get().getTags()) {
                  tags.put(featureChild.getName());
              }
              if (!tags.isEmpty()) {
                  currScenarioData.put("Tags", tags);
              }
              //Steps
              for (Step step : iterator.getScenario().get().getSteps()) {
                  Steps.put(step.getKeyword() + " " + step.getText());
              }
              if (!Steps.isEmpty()) {
                  currScenarioData.put("Steps", Steps);
              }
              //Examples
              for (Examples examples : iterator.getScenario().get().getExamples()) {
                  //Examples Headers
                  for (TableRow table : examples.getTableHeader().stream().toList()) {
                      for (TableCell ts : table.getCells()) {
                          ExamplesHeaders.put(ts.getValue());
                      }
                  }
                  Examples.put("Headers", ExamplesHeaders);
                  //Example Data
                  for (TableRow tableRow : examples.getTableBody()) {
                      JSONArray temp = new JSONArray();
                      for (TableCell s : tableRow.getCells()) {
                          temp.put(s.getValue());
                      }
                      ExamplesData.put(temp);
                  }
                  Examples.put("data", ExamplesData);
              }
              if (!Examples.isEmpty()) {
                  currScenarioData.put("Examples", Examples);
              }
              if (!ExamplesData.isEmpty()) {
                  currScenarioData.put("TestCase", iterator.getScenario().get().getName());
                  currScenarioData.put("type", "Scenario Outline");
              } else {
                  currScenarioData.put("TestCase", iterator.getScenario().get().getName());
                  currScenarioData.put("type", "Scenario");
              }
              currScenarioData.put("feature", features.getName());
              // Scenario Data
              ScenarioData.put(currScenarioData);
              //Re-Initialize Scenarios Variables
              currScenarioData = new JSONObject();
              Steps = new JSONArray();
              tags = new JSONArray();
              Examples = new JSONObject();
              ExamplesHeaders = new JSONArray();
              ExamplesData = new JSONArray();
          }
      }catch (Exception e){
          e.printStackTrace();
      }
    }

    public static JSONArray GetFeatureJsonFromGit(String GitProjectUrl){
      try{
          List<String> featureFilesUrl = GetAllFeatureFileUrlsFromGit(getApiUrl(GitProjectUrl));
          for (String s : featureFilesUrl) {
              Feature feature = GetFeatureFileFromGit(s.replace("\"", ""));
              ConvertFeatureJson(feature);
          }
      }catch (Exception e){
          e.printStackTrace();
      }
        return ScenarioData;
    }

    public static void main(String[] args){
        String gitUrl = "https://github.com/gem-pawandeep/GemEcoSystem-API-JV";
        gitUrl="https://github.com/gem-maulickbharadwaj/JewelUi-AutomationBDD";
        gitUrl="https://github.com/gem-pawandeep/TickerTapeCucumber-Updated_Version";
        System.out.println(GetFeatureJsonFromGit(gitUrl));
    }
}