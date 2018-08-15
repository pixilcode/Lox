package com.craftinginterpreters.lox;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import com.craftinginterpreters.lox.RuntimeError.InterpreterRuntimeError;
import com.craftinginterpreters.lox.RuntimeError.UserRuntimeError;

public class Lox {
	
	private static Interpreter interpreter = new Interpreter();
	
	private static boolean hadError = false;
	private static boolean hadRuntimeError = false;
	
	public static void main(String[] args) {
		
		try {
		
		// Run according to args provided
		if(args.length > 1)
			System.out.println("Usage: jlox [script]");
		else if(args.length == 1)
			runFile(args[0]);
		else
			runPrompt();
		
		} catch(IOException ioe) {
			error(args[0], 0, "File doesn't exist");
		}
		
	}
	
	private static void runFile(String path) throws IOException {
		
		// Read the file into memory
		byte[] bytes = Files.readAllBytes(Paths.get(path));
		run(path, new String(bytes, Charset.defaultCharset()));
		
		// Indicate an error in the exit code
		if(hadError)
			System.exit(65);
		if(hadRuntimeError)
			System.exit(70);
		
	}
	
	private static void runPrompt() throws IOException {
		
		BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
		
		while(true) {
			System.out.print("> ");
			run("", input.readLine());
		}
		
	}
	
	private static void run(String file, String source) {
		
		// Split the source into tokens
		Scanner scanner = new Scanner(file, source);
		List<Token> tokens = scanner.scanTokens();
		
		Parser parser = new Parser(tokens);
		List<Stmt> statements = parser.parse();
		
		// Stop if there was a syntax error
		if(hadError)
			return;
		
		Resolver resolver = new Resolver(interpreter);
		resolver.resolve(statements);
		
		// Stop if there was a resolver error
		if(hadError)
			return;
		
		Suggester suggester = new Suggester();
		suggester.suggest(statements);
		
		interpreter.interpret(statements);
		
	}
	
	/**
	 * Report an error
	 * 
	 * @param line
	 *            the line upon which the error occurs
	 * @param message
	 *            a message that goes along with the error
	 */
	static void error(String file, int line, String message) {
		report(file, line, "", "Error", message);
		hadError = true;
	}
	
	static void error(Token token, String message) {
		if(token.type == TokenType.EOF)
			report(token.file, token.line, "Error", " at end", message);
		else
			report(token.file, token.line, "Error", " at '" + token.lexeme + "'", message);
		hadError = true;
	}
	
	static void runtimeError(InterpreterRuntimeError error) {
		runtimeError(error.token, error.getMessage());
		hadRuntimeError = true;
	}
	
	static void runtimeError(Token token, String message) {
		report(token.file, token.line, "RuntimeError", " at '" + token.lexeme + "'", message);
		hadRuntimeError = true;
	}
	
	static void userError(UserRuntimeError error) {
		report(error.throwToken.file, error.throwToken.line, error.instance.klass().name, "", error.getMessage());
	}
	
	static void warning(String file, int line, String message) {
		report(file, line, "Warning", "", message);
	}
	
	static void warning(Token token, String message) {
		if(token.type == TokenType.EOF)
			report(token.file, token.line, "Warning", " at end", message);
		else
			report(token.file, token.line, "Warning", " at '" + token.lexeme + "'", message);
	}
	
	private static void report(String file, int line, String type, String where, String message) {
		if(!file.equals("")) file += ": ";
		System.err.println("[" + file + "line " + line + "] " + type + where + ": " + message);
	}
	
	static String getLibLoc() {
		
		// Get the binary location
		String loc = System.getProperty("java.class.path");
		
		// Cut off the "bin/" at the end it it's there
		if(loc.endsWith("bin/"))
			loc = loc.substring(0, loc.length()-4);
		else
			loc = loc + ".." + File.separator;
		
		// Add on "lib/"
		loc += "lib" + File.separator;
		
		return loc;
		
	}
	
}
