package com.craftinginterpreters.lox;

import java.util.List;

public class LoxFunction implements LoxCallable {
	
	private final String name;
	private final FunctionType type;
	private final List<Token> parameters;
	private final List<Stmt> body;
	private final Environment closure;
	private final boolean isInitializer;
	
	LoxFunction(Stmt.Function declaration, Environment closure, boolean isInitializer) {
		this(	declaration.name.lexeme,
				FunctionType.FUNCTION,
				declaration.parameters,
				declaration.body,
				closure,
				isInitializer);
	}
	
	LoxFunction(Expr.Lambda declaration, Environment closure, boolean isInitializer) {
		this(	"",
				FunctionType.LAMBDA,
				declaration.parameters,
				declaration.body,
				closure,
				isInitializer);
	}
	
	LoxFunction(String name, FunctionType type, List<Token> parameters, List<Stmt> body, Environment closure, boolean isInitializer) {
		
		this.name = name;
		this.type = type;
		this.parameters = parameters;
		this.body = body;
		this.closure = closure;
		this.isInitializer = isInitializer;
		
	}
	
	LoxFunction bind(LoxInstance instance) {
		
		Environment environment = new Environment(closure);
		environment.define("this", instance);
		return new LoxFunction(name, type, parameters, body, environment, isInitializer);
		
	}
	
	@Override
	public Object call(Interpreter interpreter, List<Object> arguments) {
		
		Environment environment = new Environment(closure);
		
		for(int i = 0; i < parameters.size(); i++)
			environment.define(parameters.get(i).lexeme, arguments.get(i));
		
		try {
			interpreter.executeBlock(body, environment);
		} catch(Return returnValue) {
			
			if(isInitializer)
				return closure.getThisAt(0);
			
			return returnValue.value;
		}
		
		if(isInitializer)
			return closure.getThisAt(0);
		
		return null;
		
	}
	
	@Override
	public int arity() {
		
		return parameters.size();
		
	}
	
	@Override
	public String toString() {
		
		return type.getString(name);
		
	}
	
	private enum FunctionType {
		FUNCTION, LAMBDA;
		
		String getString(String name) {
			switch(this) {
			case FUNCTION:
				return "<fn " + name + ">";
			case LAMBDA:
				return "<lambda>";
			}
			return "";
		}
	}
	
}
