package com.craftinginterpreters.lox;

public class ExitCode extends RuntimeException {
	
	private static final long serialVersionUID = 1L;
	final Token keyword;
	final Object exitCode;
	
	ExitCode(Token keyword) {
		this(keyword, 0.0);
	}
	
	ExitCode(Token keyword, Object code) {
		super(null, null, false, false);
		this.keyword = keyword;
		this.exitCode = code;
	}
	
}
