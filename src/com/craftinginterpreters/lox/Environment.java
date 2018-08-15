package com.craftinginterpreters.lox;

import java.util.HashMap;

import com.craftinginterpreters.lox.RuntimeError.InterpreterRuntimeError;

public class Environment {
	
	final Environment enclosing;
	private final HashMap<String, Object> values;
	
	Environment() {
		
		enclosing = null;
		values = new HashMap<>();
	}
	
	Environment(Environment env) {
		
		enclosing = env;
		values = new HashMap<>();
	}
	
	void define(String name, Object value) {
		values.put(name, value);
	}
	
	void assign(Token name, Object value) {
		
		if(values.containsKey(name.lexeme)) {
			values.put(name.lexeme, value);
			return;
		}
		
		if(enclosing != null) {
			enclosing.assign(name, value);
			return;
		}
		
		throw new InterpreterRuntimeError(name, "Undefined variable '" + name.lexeme + "'");
		
	}
	
	void assignAt(int distance, Token name, Object value) {
		
		ancestor(distance).values.put(name.lexeme, value);
	}
	
	Object get(Token name) {
		
		if(values.containsKey(name.lexeme))
			return values.get(name.lexeme);
		
		if(enclosing != null)
			return enclosing.get(name);
		
		throw new InterpreterRuntimeError(name, "Undefined variable '" + name.lexeme + "'");
		
	}
	
	Object getThis() {
		
		return values.get("this");
	}
	
	Object getSuper() {
		return values.get("super");
	}
	
	Object getAt(int distance, Token name) {
		
		return ancestor(distance).get(name);
	}
	
	Object getThisAt(int distance) {
		
		return ancestor(distance).getThis();
	}
	
	Object getSuperAt(int distance) {
		return ancestor(distance).getSuper();
	}
	
	private Environment ancestor(int distance) {
		
		Environment ancestor = this;
		
		for(int i = 0; i < distance; i++)
			ancestor = ancestor.enclosing;
		
		return ancestor;
		
	}
	
}
