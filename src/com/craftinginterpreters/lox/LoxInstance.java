package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.Map;

import com.craftinginterpreters.lox.RuntimeError.InterpreterRuntimeError;

public class LoxInstance {
	
	private LoxClass klass;
	private final Map<String, Object> fields;
	
	LoxInstance(LoxClass klass) {
		this.klass = klass;
		this.fields = new HashMap<>();
	}
	
	@Override
	public String toString() {
		return "<instance " + klass.name + ">";
	}
	
	Object get(Token name) {
		
		if(fields.containsKey(name.lexeme))
			return fields.get(name.lexeme);
		
		LoxFunction method = klass.findMethod(this, name.lexeme);
		if(method != null)
			return method;
		
		throw new InterpreterRuntimeError(name, "Undefined property '" + name.lexeme + "'");
		
	}
	
	void set(Token name, Object value) {
		fields.put(name.lexeme, value);
	}
	
	LoxClass klass() {
		return klass;
	}
	
}
