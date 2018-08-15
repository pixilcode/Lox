package com.craftinginterpreters.lox;

public class Token {
	
	final String directory;
	final String file;
	final TokenType type;
	final String lexeme;
	final Object literal;
	final int line;
	
	Token(String directory, String file, TokenType type, String lexeme, Object literal, int line) {
		this.directory = directory;
		this.file = file;
		this.type = type;
		this.lexeme = lexeme;
		this.literal = literal;
		this.line = line;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ( (lexeme == null) ? 0 : lexeme.hashCode());
		result = prime * result + ( (type == null) ? 0 : type.hashCode());
		return result;
	}
	
	@Override
	public boolean equals(Object obj) {
		if(this == obj)
			return true;
		if(obj == null)
			return false;
		if(! (obj instanceof Token))
			return false;
		Token other = (Token) obj;
		if(lexeme == null) {
			if(other.lexeme != null)
				return false;
		} else if(!lexeme.equals(other.lexeme))
			return false;
		if(type != other.type)
			return false;
		return true;
	}
	
	@Override
	public String toString() {
		return type + " " + lexeme + " " + literal;
	}
	
}
