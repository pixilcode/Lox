package com.craftinginterpreters.lox;

import java.util.List;
import java.util.Map;

public class LoxClass implements LoxCallable {
	
	final String name;
	final LoxClass superclass;
	private final Map<String, LoxFunction> methods;
	
	LoxClass(String name, LoxClass superclass, Map<String, LoxFunction> methods) {
		
		this.name = name;
		this.superclass = superclass;
		this.methods = methods;
		
	}
	
	LoxFunction findMethod(LoxInstance instance, String name) {
		
		if(methods.containsKey(name))
			return methods.get(name).bind(instance);
		
		if(superclass != null)
			return superclass.findMethod(instance, name);
		
		return null;
		
	}
	
	boolean inherits(Token klass) {
		
		if(klass.lexeme.equals(name))
			return true;
		
		if(superclass != null)
			return superclass.inherits(klass);
		
		return false;
		
	}
	
	@Override
	public Object call(Interpreter interpreter, List<Object> arguments) {
		
		LoxInstance instance = new LoxInstance(this);
		
		LoxFunction initializer = findMethod(instance, "init");
		if(initializer != null)
			initializer.call(interpreter, arguments);
		
		return instance;
		
	}
	
	@Override
	public int arity() {
		
		LoxFunction initializer = findMethod(new LoxInstance(this), "init");
		if(initializer == null)
			return 0;
		return initializer.arity();
		
	}
	
	@Override
	public String toString() {
		
		return "<class " + name + ">";
	}
	
}
