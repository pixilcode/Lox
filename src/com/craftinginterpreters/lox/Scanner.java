package com.craftinginterpreters.lox;

import static com.craftinginterpreters.lox.TokenType.*;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Scanner {
	
	private final String directory;
	private final String file;
	private final String source;
	private final List<Token> tokens;
	
	// Reserved words
	private static final Map<String, TokenType> keywords;
	
	static {
		keywords = new HashMap<>();
		keywords.put("and", AND);
		keywords.put("class", CLASS);
		keywords.put("catch", CATCH);
		keywords.put("else", ELSE);
		keywords.put("exit", EXIT);
		keywords.put("false", FALSE);
		keywords.put("finally", FINALLY);
		keywords.put("for", FOR);
		keywords.put("fn", FUN);
		keywords.put("if", IF);
		keywords.put("import", IMPORT);
		keywords.put("include", INCLUDE);
		keywords.put("nil", NIL);
		keywords.put("or", OR);
		keywords.put("print", PRINT);
		keywords.put("return", RETURN);
		keywords.put("super", SUPER);
		keywords.put("this", THIS);
		keywords.put("throw", THROW);
		keywords.put("true", TRUE);
		keywords.put("try", TRY);
		keywords.put("var", VAR);
		keywords.put("while", WHILE);
	}
	
	// Location in source code
	private int start;
	private int current;
	private int line;
	
	Scanner(String file, String source) {
		
		String[] fileLoc;
		
		if(File.separator.equals("\\"))
			fileLoc = file.split("\\\\");
		else
			fileLoc = file.split(File.separator);
		
		String dirName = "";
		for(int i = 0; i < fileLoc.length-1; i++)
			dirName += fileLoc[i] + File.separator;
		
		String fileName = fileLoc[fileLoc.length-1];
		
		this.directory = dirName;
		this.file = fileName + ((fileName.endsWith(".lox")) ? "" : ".lox");
		this.source = source;
		this.tokens = new ArrayList<>();
		
		start = 0;
		current = 0;
		line = 1;
		
	}
	
	List<Token> scanTokens() {
		
		while(!isAtEnd()) {
			
			// We are at the beginning of the next lexeme
			start = current;
			scanToken();
			
		}
		
		tokens.add(new Token(directory, file, EOF, "", null, line));
		return tokens;
		
	}
	
	private void scanToken() {
		
		char c = advance();
		
		switch(c) {
		case '(':
			addToken(LEFT_PAREN);
			break;
		case ')':
			addToken(RIGHT_PAREN);
			break;
		case '{':
			addToken(LEFT_BRACE);
			break;
		case '}':
			addToken(RIGHT_BRACE);
			break;
		case ',':
			addToken(COMMA);
			break;
		case '.':
			addToken(DOT);
			break;
		case '-':
			addToken(MINUS);
			break;
		case '+':
			addToken(PLUS);
			break;
		case ';':
			addToken(SEMICOLON);
			break;
		case '*':
			addToken(STAR);
			break;
		case '!':
			addToken(match('=') ? BANG_EQUAL : BANG);
			break;
		case '=':
			addToken(match('=') ? EQUAL_EQUAL : EQUAL);
			break;
		case '<':
			addToken(match('=') ? LESS_EQUAL : LESS);
			break;
		case '>':
			addToken(match('=') ? GREATER_EQUAL : GREATER);
			break;
		case '&':
			addToken(AMPERSAND);
			break;
		case '|':
			addToken(PIPE);
			break;
		
		// Comments
		case '/':
			if(match('/'))
				while(peek() != '\n' && !isAtEnd())
					advance();
			else if(match('*')) {
				while(!(peek() == '*' && peekNext() == '/') && !isAtEnd())
					advance();
				advance();
				advance();
				advance();
			} else
				addToken(SLASH);
			break;
		
		// Ignore whitespace
		case ' ':
		case '\r':
		case '\t':
			break;
		
		case '\n':
			line++;
			break;
		
		case '"':
			string();
			break;
		
		default:
			if(isDigit(c))
				number();
			else if(isAlpha(c))
				identifier();
			else
				Lox.error(file, line, "Unexpected character.");
			break;
		}
		
	}
	
	private void identifier() {
		
		while(isAlphaNumeric(peek()))
			advance();
		
		// Determine if the identifier is a reserved word
		String text = source.substring(start, current);
		
		TokenType type = keywords.get(text);
		if(type == null)
			type = IDENTIFIER;
		
		addToken(type);
		
	}
	
	private void string() {
		
		// The starting line of the string, for error reporting
		int startLine = line;
		
		// Continue to consume until end condition is reached
		while(peek() != '"' && !isAtEnd()) {
			if(peek() == '\n')
				line++;
			if(peek() == '\\' && peekNext() == '"') // If there is an escaped string ahead, skip it
				advance();
			advance();
		}
		
		// Unterminated string
		if(isAtEnd()) {
			Lox.error(file, startLine, "Unterminated string");
			return;
		}
		
		// Skip closing '"'
		advance();
		
		// Trim surrounding quotes
		String value = replaceEscapeCharacters(source.substring(start + 1, current - 1));
		addToken(STRING, value);
		
	}
	
	private void number() {
		
		while(isDigit(peek()))
			advance();
		
		// Look for fractional part
		if(peek() == '.' && isDigit(peekNext())) {
			advance();
			while(isDigit(peek()))
				advance();
		}
		
		addToken(NUMBER, Double.parseDouble(source.substring(start, current)));
		
	}
	
	private String replaceEscapeCharacters(String source) {
		
		Map<String, String> escapes = new HashMap<>();
		escapes.put("\\t", "\t");
		escapes.put("\\n", "\n");
		escapes.put("\\r", "\r");
		escapes.put("\\b", "\b");
		escapes.put("\\f", "\f");
		escapes.put("\\'", "\'");
		escapes.put("\\\"", "\"");
		
		for(String escapeSeq : escapes.keySet())
			source = source.replace(escapeSeq, escapes.get(escapeSeq));
		
		if(source.matches(".*\\\\[^\\\\].*"))
			Lox.error(file, line, "Invalid escape sequence");
		
		source = source.replace("\\\\", "\\");
		
		return source;
		
	}
	
	private char advance() {
		return source.charAt(current++);
	}
	
	private boolean match(char expected) {
		
		// If the scanner has reached the end of the file
		// or if the next character does not match the expected character
		// it doesn't match
		if(isAtEnd() || source.charAt(current) != expected)
			return false;
		
		// If it does match, consume it
		current++;
		return true;
		
	}
	
	private char peek() {
		
		if(isAtEnd())
			return '\0';
		return source.charAt(current);
		
	}
	
	private char peekNext() {
		if(current + 1 >= source.length())
			return '\0';
		return source.charAt(current + 1);
	}
	
	private boolean isAlpha(char c) {
		return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
	}
	
	private boolean isDigit(char c) {
		return (c >= '0' && c <= '9');
	}
	
	private boolean isAlphaNumeric(char c) {
		return isAlpha(c) || isDigit(c);
	}
	
	private boolean isAtEnd() {
		return current >= source.length();
	}
	
	private void addToken(TokenType type) {
		addToken(type, null);
	}
	
	private void addToken(TokenType type, Object literal) {
		String text = source.substring(start, current);
		tokens.add(new Token(directory, file, type, text, literal, line));
	}
	
}
