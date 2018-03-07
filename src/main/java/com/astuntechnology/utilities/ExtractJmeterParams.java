package com.astuntechnology.utilities;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URLDecoder;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.MissingOptionException;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;

public class ExtractJmeterParams {
	enum TYPE {
		APACHE, MICROSOFT;
	}

	private static final boolean debug = false;

	/**
	 * Process an access log file to create input for a JMeter session
	 * 
	 * A bit like LogParser but portable
	 * 
	 * @param args
	 */

	private ArrayList<String> columns = new ArrayList<>();
	private TreeMap<Calendar, HashMap<String, String>> results = new TreeMap<>();
	private static SimpleDateFormat asdf = new SimpleDateFormat("dd/MMM/yyyy:HH:mm:ssZ");
	private static SimpleDateFormat msdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	private String seperator = "\t";
	private TYPE type = TYPE.APACHE;
	private String column = "URL";
	private String value = "print";
	private String exclude = "";
	private boolean incDate = false;

	public static void main(String[] args) throws IOException, ParseException, org.apache.commons.cli.ParseException {
		Options options = new Options();
		options.addOption(
				Option.builder("f").longOpt("file").hasArgs().desc("log files to read in").required().build());
		options.addOption(Option.builder("o").longOpt("output").hasArg().desc("Output file (default StdOut)").build());
		options.addOption(Option.builder("c").longOpt("columns").hasArgs()
				.desc("space seperated list of parameters to extract from the url").required().build());
		options.addOption(Option.builder("s").longOpt("separator").hasArg()
				.desc("character to separate output columns (default tab)").build());
		options.addOption(Option.builder("m").longOpt("microsoft").desc("Is this a microsoft log file").build());
		options.addOption(Option.builder("t").longOpt("term").hasArg()
				.desc("Word that must occur in url to be selected").build());
		options.addOption(Option.builder("x").longOpt("exclude").hasArg()
				.desc("regexp which if matches url excludes record").build());
		options.addOption(Option.builder().longOpt("timing").desc("print the timing info").build());
		options.addOption(Option.builder("d").longOpt("date").desc("include date/time stamp in output").build());
		CommandLineParser parser = new DefaultParser();
		CommandLine cmd = null;
		try {
			cmd = parser.parse(options, args);
		} catch (MissingOptionException e) {
			String header = "Extract paramters from a log file\n\n";
			String footer = "\nPlease report issues to Ian";

			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("ExtractJmeterParams", header, options, footer, true);
			System.exit(1);
		}
		ExtractJmeterParams me = new ExtractJmeterParams();
		if (cmd.hasOption("term")) {
			me.setMatch(cmd.getOptionValue("term"));
		}
		if (cmd.hasOption('x')) {
			me.setExclude(cmd.getOptionValue("exclude"));
		}
		if (cmd.hasOption("m")) {
			me.setType(TYPE.MICROSOFT);
		}
		if(cmd.hasOption("d")) {
			me.setDate(true);
		}
		String[] cols = cmd.getOptionValues("c");
		String[] inputFiles = new String[] {};
		if (cmd.hasOption('f')) {
			inputFiles = cmd.getOptionValues("f");
		}
		if (cmd.hasOption('s')) {
			me.setSeperator(cmd.getOptionValue('s'));
		}
		PrintStream out = System.out;
		try {

			if (cmd.hasOption('o')) {
				String outputFile = cmd.getOptionValue("o");
				File outf = new File(outputFile);
				out = new PrintStream(new FileOutputStream(outf));
			}

			me.setColumns(cols);
			me.parseFile(inputFiles);

			me.print(out);
		} finally {
			if (out != System.out) {
				out.close();
			}
		}
		if(cmd.hasOption("timing")) {
			me.getTimings();
		}
	}

	private void setDate(boolean b) {
		incDate = true;
	}

	public void setColumns(String[] strings) {
		columns = new ArrayList<>();
		for (String col : strings) {
			columns.add(col.toUpperCase());
		}
	}

	public String[] getColumns() {
		return columns.toArray(new String[] {});
	}

	public Map<Calendar, HashMap<String, String>> getResults() {
		return results;
	}

	public void parseFile(String[] inputFiles) throws FileNotFoundException, IOException, ParseException {
		results = new TreeMap<>();
		String colm = column == null ? null : column.toUpperCase();
		String val = value == null ? null : value.toLowerCase();
		Pattern pURL;
		Pattern pTime,exlusion = null;
		DateFormat sdf;
		if (type.equals(TYPE.APACHE)) {
			pURL = Pattern.compile("^.*\"GET (.+) HTTP.*$");
			pTime = Pattern.compile("^.*\\[(.+)\\].*$");
			sdf = asdf;
		} else {
			pURL = Pattern.compile("^.*GET (.+?) (.+?) \\d{2,4} .*$");
			
			pTime = Pattern.compile("^([\\d-]+ [\\d:]+) .*$");
			sdf = msdf;
		}
		try {
			exlusion = Pattern.compile(exclude);
		} catch (PatternSyntaxException e) {
			System.err.println("unable to compile exclusion pattern '"+exclude+"'");
			System.err.println(e.getLocalizedMessage());
			System.exit(3);
		}
		Pattern inclusion=null;
		try {
			inclusion = Pattern.compile(val);
		} catch (PatternSyntaxException e) {
			System.err.println("unable to compile exclusion pattern '"+exclude+"'");
			System.err.println(e.getLocalizedMessage());
			System.exit(3);
		}
		for (String inputFile : inputFiles) {

			int lineNo = 0;

			try (BufferedReader reader = new BufferedReader(new FileReader(new File(inputFile)))) {
				String line;

				while ((line = reader.readLine()) != null) {
					lineNo++;
					if(debug)
						System.out.println(lineNo+":"+line);
					if (line.startsWith("#")) {
						// Microsoft IIS includes "comments" in the file!
						continue;
					}
					HashMap<String, String> params = new HashMap<>();

					Matcher d = pTime.matcher(line);
					Calendar date = Calendar.getInstance();
					if (d.matches()) {
						String dateString = null;
						try {
							dateString = d.group(1);
							if (type.equals(TYPE.APACHE)) {
								dateString = dateString.replaceAll(" ", "");
							}
							date.setTime(sdf.parse(dateString));
						} catch (java.text.ParseException e) {
							System.err
									.println("Unparsable date: " + dateString + " at line no:" + lineNo + "\n" + line);
							continue;
						}
					}
					Matcher m = pURL.matcher(line);
					if (m.matches()) {
						String query = null;
						try {
							String url;
							if (TYPE.APACHE == type) {
								url = m.group(1);
							} else if (TYPE.MICROSOFT == type) {
								if (m.group(2).equalsIgnoreCase("-")) {
									url = m.group(1);
								} else {
									url = m.group(1) + "?" + m.group(2);
								}
							} else {
								throw new IllegalStateException("Unknown type set");
							}
							query = URLDecoder.decode(url, "UTF-8");
						} catch (IllegalArgumentException e) {
							System.err.println("Unparsable URL: " + query + " at line no:" + lineNo + "\n" + line);
							continue;
						}
						// System.out.println(query);
						String[] parts = query.split("&|;|\\?| ");
						boolean first = true;
						for (String part : parts) {
							if (first) {
								first = false;
								params.put("URL", part);
								if (!columns.contains("URL")) {
									columns.add(0, "URL");
								}
							}
							String[] bits = part.split("=");
							if (bits.length > 1) {
								params.put(bits[0].toUpperCase(), bits[1]);
							} else {
								if (bits.length == 1)
									params.put(bits[0], null);
							}
						}
						while (results.containsKey(date)) {// separate the
															// requests
															// (assume no more
															// than
															// 1000 reqs per s)
							date.add(Calendar.MILLISECOND, 1);
						}
						Matcher e = null;
						if(!exclude.isEmpty()) {
							 e = exlusion.matcher(params.get(colm).toLowerCase());
						}
						if (exclude.isEmpty()||!e.find()) {
							if (colm != null) {
								if (val != null) {
									
									Matcher i = inclusion.matcher(params.get(colm).toLowerCase());
									if (params.containsKey(colm) && i.find()) {
										results.put(date, params);
									}
								} else {
									if (params.containsKey(colm)) {
										results.put(date, params);
									}
								}
							} else {
								results.put(date, params);
							}
						} else { // exclude

						}

					}
				}
			}
		}

	}

	public void print() {
		print(System.out);
	}

	public void print(PrintStream out) {

		for (String c : getColumns()) {
			out.print(c + seperator);
		}
		out.println();
		for (Calendar d : results.keySet()) {
			HashMap<String, String> params = results.get(d);
			if(incDate) {
				out.print(msdf.format(d.getTime())+seperator);
			}
			for (String col : getColumns()) {
				if (params.containsKey(col)) {
					out.print(params.get(col) + seperator);
				} else {
					out.print(seperator);
				}
			}
			out.println();
		}

	}

	public String getSeperator() {
		return seperator;
	}

	public void setSeperator(String seperator) {
		this.seperator = seperator;
	}

	public TYPE getType() {
		return type;
	}

	public void setType(TYPE type) {
		this.type = type;
	}

	public String getMatch() {
		return value;
	}

	public void setMatch(String match) {
		this.value = match;
	}

	public void getTimings() {
		Queue<Date> times = new LinkedList<>();
		int maxReq = 0;
		Date first = null,last = null;
		int count =0;
		for (Calendar key : results.keySet()) {
			Date d = key.getTime();
			times.add(d);
			if(first==null||first.after(d)) {
				first=d;
			}
			if(last==null||last.before(d)) {
				last=d;
			}
			Date top = times.peek();
			long duration = ChronoUnit.MINUTES.between(top.toInstant(), d.toInstant());
			if(duration>=1) {
				times.poll();
				if(maxReq<times.size()) {
					maxReq = times.size();
				}
			}
			count++;
		}
		System.out.println("Range:"+first+"->"+last+" "+count+" requests");
		
		long duration = ChronoUnit.MINUTES.between(first.toInstant(), last.toInstant());
		System.out.println(duration+" minutes");
		System.out.println("Peak req "+maxReq+" per minute");
	}

	public String getExclude() {
		return exclude;
	}

	public void setExclude(String exclude) {
		this.exclude = exclude;
	}
}
