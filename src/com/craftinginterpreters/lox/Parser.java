package com.craftinginterpreters.lox;

import static com.craftinginterpreters.lox.TokenType.*;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Parser {
	
	private final Map<String, Stmt.Include> includes = new HashMap<>();
	private final Map<String, Stmt.Import> imports = new HashMap<>();
	
	private final List<Token> tokens;
	private final State state;
	private int current;
	
	Parser(List<Token> tokens) {
		this.tokens = tokens;
		this.current = 0;
		this.state = State.NORMAL;
	}
	
	Parser(List<Token> tokens, State state) {
		this.tokens = tokens;
		this.current = 0;
		this.state = state;
	}
	
	List<Stmt> parse() {
		
		List<Stmt> statements = new ArrayList<Stmt>();
		
		while(!isAtEnd()) {
			
			Stmt decl;
			
			if(state == State.IMPORT) {
				do {
					decl = declaration();
				} while(!(decl instanceof Stmt.Class || decl instanceof Stmt.Import) && !isAtEnd());
				if(decl instanceof Stmt.Class  || decl instanceof Stmt.Import)
					statements.add(decl);
			} else {
				statements.add(declaration());
			}
		}
		
		return statements;
		
	}
	
	private Stmt declaration() {
		
		try {
			
			if(match(CLASS))
				return classDeclaration();
			if(match(FUN))
				return function("function");
			if(match(VAR))
				return varDeclaration();
			
			return statement();
			
		} catch(ParseError error) {
			synchronize();
			return null;
		}
		
	}
	
	private Stmt classDeclaration() {
		
		Token name = consume(IDENTIFIER, "Expected class name");
		
		Expr.Variable superclass = null;
		if(match(LESS)) {
			consume(IDENTIFIER, "Expected super class name");
			superclass = new Expr.Variable(previous());
		}
		
		consume(LEFT_BRACE, "Expected '{' before class body");
		
		List<Stmt.Function> methods = new ArrayList<>();
		while(!check(RIGHT_BRACE) && !isAtEnd())
			methods.add((Stmt.Function) function("method"));
		
		consume(RIGHT_BRACE, "Expected '}' after class body");
		
		return new Stmt.Class(name, superclass, methods);
		
	}
	
	private Stmt function(String type) {
		
		Token name = consume(IDENTIFIER, "Expected " + type + " name");
		consume(LEFT_PAREN, "Expected '(' after " + type + " name");
		
		List<Token> parameters = new ArrayList<>();
		
		if(!check(RIGHT_PAREN)) {
			
			do {
				
				if(parameters.size() > 8)
					error(peek(), "Cannot have more than 8 parameters");
				
				parameters.add(consume(IDENTIFIER, "Expected parameter name"));
				
			} while(match(COMMA));
			
		}
		
		consume(RIGHT_PAREN, "Expected ')' after parameters");
		
		consume(LEFT_BRACE, "Expected '{' before " + type + " body");
		List<Stmt> body = block();
		
		return new Stmt.Function(name, parameters, body);
		
	}
	
	private Stmt varDeclaration() {
		
		Token name = consume(IDENTIFIER, "Expected variable name");
		
		Expr initializer = null;
		if(match(EQUAL))
			initializer = expression();
		
		consume(SEMICOLON, "Expected ';' after variable declaration");
		return new Stmt.Var(name, initializer);
		
	}
	
	private Stmt statement() {
		
		if(match(IF))
			return ifStatement();
		if(match(IMPORT))
			return importStatement();
		if(match(INCLUDE))
			return includeStatement();
		if(match(FOR))
			return forStatement();
		if(match(EXIT))
			return exitStatement();
		if(match(PRINT))
			return printStatement();
		if(match(RETURN))
			return returnStatement();
		if(match(THROW))
			return throwStatement();
		if(match(TRY))
			return tryStatement();
		if(match(WHILE))
			return whileStatement();
		if(match(LEFT_BRACE))
			return new Stmt.Block(block());
		
		return expressionStatement();
		
	}
	
	private Stmt ifStatement() {
		
		Token keyword = previous();
		
		consume(LEFT_PAREN, "Expected '(' after 'if'");
		Expr condition = expression();
		consume(RIGHT_PAREN, "Expected ')' after condition");
		
		Stmt thenBranch = statement();
		Stmt elseBranch = null;
		
		if(match(ELSE))
			elseBranch = statement();
		
		return new Stmt.If(keyword, condition, thenBranch, elseBranch);
		
	}
	
	private Stmt importStatement() {
		
		Token keyword = previous();
		
		String loc = (String) consume(STRING, "Expected a string containing the location of the file to import").literal;
		
		try {
			
			String file = keyword.directory + loc + ".lox";
			
			if(imports.containsKey(file)) {
				if(imports.get(file) != null)
					return imports.get(file);
				else
					error(keyword, "Recursive code importing in file '" + file + "'");
			}
			
			imports.put(file, null);
			
			byte[] bytes = Files.readAllBytes(Paths.get(file));
			String code = new String(bytes, Charset.defaultCharset());
			
			Scanner scanner = new Scanner(file, code);
			List<Token> tokens = scanner.scanTokens();
			
			Parser parser = new Parser(tokens, State.IMPORT);
			List<Stmt> body = parser.parse();
			
			consume(SEMICOLON, "Expected ';' after import statement");
			
			Stmt.Import stmt = new Stmt.Import(keyword, file, body);
			
			imports.put(file, stmt);
			
			return stmt;
			
		} catch(IOException ioe) {
			
			String file = Lox.getLibLoc() + loc + ".lox";
			
			try {
				
				if(imports.containsKey(file)) {
					if(imports.get(file) != null)
						return imports.get(file);
					else
						error(keyword, "Recursive importing in file '" + file + "'");
				}
				
				imports.put(file, null);
				
				byte[] bytes = Files.readAllBytes(Paths.get(file));
				String code = new String(bytes, Charset.defaultCharset());
				
				Scanner scanner = new Scanner(file, code);
				List<Token> tokens = scanner.scanTokens();
				
				Parser parser = new Parser(tokens, State.IMPORT);
				List<Stmt> body = parser.parse();
				
				consume(SEMICOLON, "Expected ';' after import statement");
				
				Stmt.Import stmt = new Stmt.Import(keyword, file, body);
				
				imports.put(file, stmt);
				
				return stmt;
				
			} catch(IOException ioe2) {
				file = keyword.directory + loc + ".lox";
				error(keyword, "Cannot find '" + file + "'");
			} catch(StackOverflowError soe) {
				error(keyword, "Recursive code importing in file '" + file + "'");
			}
			
		} catch(StackOverflowError soe) {
			error(keyword, "Recursive code importing in file '" + keyword.directory + loc + ".lox" + "'");
		}
		
		return null;
		
	}
	
	private Stmt includeStatement() {
		
		Token keyword = previous();
		
		String loc = (String) consume(STRING, "Expected a string containing the location of the file to include").literal;
		
		try {
			
			String file = keyword.directory + loc + ".lox";
			
			if(includes.containsKey(file)) {
				if(includes.get(file) != null)
					return includes.get(file);
				else
					error(keyword, "Recursive code inclusion in file '" + file + "'");
			}
			
			includes.put(file, null);
			
			byte[] bytes = Files.readAllBytes(Paths.get(file));
			String code = new String(bytes, Charset.defaultCharset());
			
			Scanner scanner = new Scanner(file, code);
			List<Token> tokens = scanner.scanTokens();
			
			Parser parser = new Parser(tokens, State.INCLUDE);
			List<Stmt> body = parser.parse();
			
			consume(SEMICOLON, "Expected ';' after include statement");
			
			Stmt.Include stmt = new Stmt.Include(keyword, file, body);
			
			includes.put(file, stmt);
			
			return stmt;
			
		} catch(IOException ioe) {
			
			String file = Lox.getLibLoc() + loc + ".lox";
			
			try {
				
				if(includes.containsKey(file)) {
					if(includes.get(file) != null)
						return includes.get(file);
					else
						error(keyword, "Recursive code inclusion in file '" + file + "'");
				}
				
				includes.put(file, null);
				
				byte[] bytes = Files.readAllBytes(Paths.get(file));
				String code = new String(bytes, Charset.defaultCharset());
				
				Scanner scanner = new Scanner(file, code);
				List<Token> tokens = scanner.scanTokens();
				
				Parser parser = new Parser(tokens, State.INCLUDE);
				List<Stmt> body = parser.parse();
				
				consume(SEMICOLON, "Expected ';' after include statement");
				
				Stmt.Include stmt = new Stmt.Include(keyword, file, body);
				
				includes.put(file, stmt);
				
				return stmt;
				
			} catch(IOException ioe2) {
				file = keyword.directory + loc + ".lox";
				error(keyword, "Cannot find '" + file + "'");
			} catch(StackOverflowError soe) {
				error(keyword, "Recursive code inclusion in file '" + file + "'");
			}
			
		} catch(StackOverflowError soe) {
			error(keyword, "Recursive code inclusion in file '" + keyword.directory + loc + ".lox" + "'");
		}
		
		return null;
		
	}
	
	private Stmt forStatement() {
		
		Token keyword = previous();
		
		consume(LEFT_PAREN, "Expected '(' after 'for'");
		
		Stmt initializer;
		if(match(SEMICOLON))
			initializer = null;
		else if(match(VAR))
			initializer = varDeclaration();
		else
			initializer = expressionStatement();
		
		Expr condition = null;
		if(!check(SEMICOLON))
			condition = expression();
		consume(SEMICOLON, "Expected ';' after loop condition");
		
		Expr increment = null;
		if(!check(RIGHT_PAREN))
			increment = expression();
		consume(RIGHT_PAREN, "Expected ')' after for clauses");
		
		Stmt body = statement();
		
		if(increment != null) {
			body = new Stmt.Block(Arrays.asList(body, new Stmt.Expression(increment)));
		}
		
		if(condition == null)
			condition = new Expr.Literal(true);
		body = new Stmt.While(keyword, condition, body);
		
		if(initializer != null)
			body = new Stmt.Block(Arrays.asList(initializer, body));
		
		return body;
		
	}
	
	private Stmt exitStatement() {
		
		Token keyword = previous();
		Expr code = null;
		
		if(!check(SEMICOLON))
			code = expression();
		
		consume(SEMICOLON, "Expected ';' after exit statement");
		
		return new Stmt.Exit(keyword, code);
		
	}
	
	private Stmt printStatement() {
		Token keyword = previous();
		Expr value = expression();
		consume(SEMICOLON, "Expected ';' after value");
		return new Stmt.Print(keyword, value);
	}
	
	private Stmt returnStatement() {
		
		Token keyword = previous();
		Expr value = null;
		
		if(!check(SEMICOLON))
			value = expression();
		
		consume(SEMICOLON, "Expected ';' after return value");
		return new Stmt.Return(keyword, value);
		
	}
	
	private Stmt throwStatement() {
		
		Token keyword = previous();
		Expr thrown = expression();
		
		consume(SEMICOLON, "Expected ';' after throw statement");
		
		return new Stmt.Throw(keyword, thrown);
		
	}
	
	private Stmt tryStatement() {
		
		Token keyword = previous();
		
		Stmt body = statement();
		
		List<Stmt.Catch> catches = new ArrayList<>();
		
		while(match(CATCH)) {
			
			Token catchKeyword = previous();
			
			consume(LEFT_PAREN, "Expected '(' after 'catch'");
			
			List<Token> errors = new ArrayList<>();
			
			errors.add(consume(IDENTIFIER, "Expected error class identifier"));
			while(match(COMMA))
				errors.add(consume(IDENTIFIER, "Expected error class identifier"));
			Token identifier = consume(IDENTIFIER, "Expected variable identifier");
			
			consume(RIGHT_PAREN, "Expected ')' after error identifier");
			
			Stmt catchBody = statement();
			
			catches.add(new Stmt.Catch(catchKeyword, errors, identifier, catchBody));
			
		}
		
		Stmt finallyStmt = null;
		if(match(FINALLY))
			finallyStmt = statement();
		
		return new Stmt.Try(keyword, body, catches, finallyStmt);
		
	}
	
	private Stmt whileStatement() {
		
		Token keyword = previous();
		
		consume(LEFT_PAREN, "Expected '(' after 'while'");
		Expr condition = expression();
		consume(RIGHT_PAREN, "Expected ')' after condition");
		Stmt body = statement();
		
		return new Stmt.While(keyword, condition, body);
		
	}
	
	private Stmt expressionStatement() {
		
		Expr expr = expression();
		consume(SEMICOLON, "Expected ';' after value");
		return new Stmt.Expression(expr);
	}
	
	private List<Stmt> block() {
		
		List<Stmt> statements = new ArrayList<>();
		
		while(!check(RIGHT_BRACE) && !isAtEnd())
			statements.add(declaration());
		
		consume(RIGHT_BRACE, "Expected '}' after block");
		return statements;
		
	}
	
	private Expr expression() {
		
		return assignment();
	}
	
	private Expr assignment() {
		
		Expr expr = lambda();
		
		if(match(EQUAL)) {
			
			Token equals = previous();
			Expr value = assignment();
			
			if(expr instanceof Expr.Variable) {
				Token name = ((Expr.Variable) expr).name;
				return new Expr.Assign(name, value);
			} else if(expr instanceof Expr.Get) {
				Expr.Get get = (Expr.Get) expr;
				return new Expr.Set(get.object, get.name, value);
			}
			
			error(equals, "Invalid assignment target");
			
		}
		
		return expr;
		
	}
	
	private Expr lambda() {
		
		if(match(PIPE)) {
			
			List<Token> parameters = new ArrayList<>();
			
			if(!check(PIPE)) {
				
				do {
					
					if(parameters.size() > 8)
						error(peek(), "Cannot have more than 8 parameters");
					
					parameters.add(consume(IDENTIFIER, "Expected parameter name"));
					
				} while(match(COMMA));
				
			}
			
			consume(PIPE, "Expected '|' after lambda parameters");
			
			Token start = consume(LEFT_BRACE, "Expected '{' before lambda body");
			List<Stmt> body = block();
			
			return new Expr.Lambda(start, parameters, body);
		}
		
		return or();
		
	}
	
	private Expr or() {
		
		Expr expr = and();
		
		while(match(OR) || match(PIPE)) {
			Token operator = previous();
			Expr right = and();
			expr = new Expr.Logical(expr, operator, right);
		}
		
		return expr;
		
	}
	
	private Expr and() {
		
		Expr expr = equality();
		
		while(match(AND) || match(AMPERSAND)) {
			Token operator = previous();
			Expr right = and();
			expr = new Expr.Logical(expr, operator, right);
		}
		
		return expr;
		
	}
	
	private Expr equality() {
		
		Expr expr = comparison();
		
		while(match(BANG_EQUAL, EQUAL_EQUAL)) {
			Token operator = previous();
			Expr right = comparison();
			expr = new Expr.Binary(expr, operator, right);
		}
		
		return expr;
		
	}
	
	private Expr comparison() {
		
		Expr expr = addition();
		
		while(match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
			Token operator = previous();
			Expr right = addition();
			expr = new Expr.Binary(expr, operator, right);
		}
		
		return expr;
		
	}
	
	private Expr addition() {
		
		Expr expr = multiplication();
		
		while(match(PLUS, MINUS)) {
			Token operator = previous();
			Expr right = multiplication();
			expr = new Expr.Binary(expr, operator, right);
		}
		
		return expr;
		
	}
	
	private Expr multiplication() {
		
		Expr expr = unary();
		
		while(match(STAR, SLASH)) {
			Token operator = previous();
			Expr right = unary();
			expr = new Expr.Binary(expr, operator, right);
		}
		
		return expr;
		
	}
	
	private Expr unary() {
		
		if(match(BANG, MINUS)) {
			Token operator = previous();
			Expr right = unary();
			return new Expr.Unary(operator, right);
		}
		
		return call();
		
	}
	
	private Expr call() {
		
		Expr expr = primary();
		
		while(true) {
			
			if(match(LEFT_PAREN))
				expr = finishCall(expr);
			else if(match(DOT)) {
				Token name = consume(IDENTIFIER, "Expected property name after '.'");
				expr = new Expr.Get(expr, name);
			} else
				break;
			
		}
		
		return expr;
		
	}
	
	private Expr primary() {
		
		if(match(NUMBER, STRING))
			return new Expr.Literal(previous().literal);
		if(match(TRUE))
			return new Expr.Literal(true);
		if(match(FALSE))
			return new Expr.Literal(false);
		if(match(NIL))
			return new Expr.Literal(null);
		if(match(THIS))
			return new Expr.This(previous());
		if(match(SUPER)) {
			Token keyword = previous();
			consume(DOT, "Expected '.' after 'super'");
			Token method = consume(IDENTIFIER, "Expected superclass method name");
			return new Expr.Super(keyword, method);
		}
		
		// TODO Make filler check here for partial application
		if(match(IDENTIFIER))
			return new Expr.Variable(previous());
		
		if(match(LEFT_PAREN)) {
			Expr expr = expression();
			consume(RIGHT_PAREN, "Expected ')' after expression");
			return new Expr.Grouping(expr);
		}
		
		throw error(peek(), "Expected expression");
		
	}
	
	private Expr finishCall(Expr callee) {
		
		List<Expr> arguments = new ArrayList<>();
		
		if(!check(RIGHT_PAREN)) {
			
			if(arguments.size() >= 8)
				error(peek(), "Cannot have more than 8 arguments");
			
			do {
				arguments.add(expression());
			} while(match(COMMA));
			
		}
		
		Token paren = consume(RIGHT_PAREN, "Expected ')' after arguments");
		
		return new Expr.Call(callee, paren, arguments);
		
	}
	
	private boolean match(TokenType... types) {
		
		for(TokenType type : types) {
			if(check(type)) {
				advance();
				return true;
			}
		}
		
		return false;
		
	}
	
	private Token advance() {
		
		if(!isAtEnd())
			current++;
		return previous();
		
	}
	
	private Token consume(TokenType type, String message) {
		
		if(check(type))
			return advance();
		
		throw error(peek(), message);
		
	}
	
	private Token peek() {
		
		return tokens.get(current);
	}
	
	private Token previous() {
		
		return tokens.get(current - 1);
	}
	
	private boolean check(TokenType type) {
		
		if(isAtEnd())
			return false;
		return peek().type == type;
		
	}
	
	private boolean isAtEnd() {
		
		return peek().type == EOF;
	}
	
	private ParseError error(Token token, String message) {
		
		Lox.error(token, message);
		return new ParseError();
	}
	
	private void synchronize() {
		
		advance();
		
		while(!isAtEnd()) {
			
			if(previous().type == SEMICOLON)
				return;
			
			switch(peek().type) {
			case CLASS:
			case FUN:
			case VAR:
			case FOR:
			case IF:
			case WHILE:
			case PRINT:
			case RETURN:
			case EXIT:
				return;
			default:
			}
			
			advance();
			
		}
		
	}
	
	private static enum State {
		NORMAL,
		IMPORT,
		INCLUDE;
	}
	
	private static class ParseError extends RuntimeException {
		
		private static final long serialVersionUID = 1L;
	}
	
}
