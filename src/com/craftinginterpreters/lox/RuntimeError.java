package com.craftinginterpreters.lox;

public abstract class RuntimeError extends RuntimeException {
	
	private static final long serialVersionUID = 1L;
	
	RuntimeError(String message) {
		super(message);
	}
	
	public static class InterpreterRuntimeError extends RuntimeError {
		
		private static final long serialVersionUID = 1L;
		final Token token;
		boolean catchable;
		
		InterpreterRuntimeError(Token token, String message) {
			super(message);
			this.token = token;
			this.catchable = true;
		}
		
		public InterpreterRuntimeError(Token token, String message, boolean catchable) {
			this(token, message);
			this.catchable = catchable;
		}
		
	}
	
	public static class UserRuntimeError extends RuntimeError {
		
		private static final long serialVersionUID = 1L;
		LoxInstance instance;
		Token throwToken;
		
		UserRuntimeError(LoxInstance instance, String message, Token throwToken) {
			super(message);
			this.instance = instance;
			this.throwToken = throwToken;
		}
		
	}

}
