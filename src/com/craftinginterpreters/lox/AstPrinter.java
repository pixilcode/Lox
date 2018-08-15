package com.craftinginterpreters.lox;

import com.craftinginterpreters.lox.Expr.Assign;
import com.craftinginterpreters.lox.Expr.Binary;
import com.craftinginterpreters.lox.Expr.Call;
import com.craftinginterpreters.lox.Expr.Get;
import com.craftinginterpreters.lox.Expr.Grouping;
import com.craftinginterpreters.lox.Expr.Lambda;
import com.craftinginterpreters.lox.Expr.Literal;
import com.craftinginterpreters.lox.Expr.Logical;
import com.craftinginterpreters.lox.Expr.Set;
import com.craftinginterpreters.lox.Expr.Super;
import com.craftinginterpreters.lox.Expr.This;
import com.craftinginterpreters.lox.Expr.Unary;
import com.craftinginterpreters.lox.Expr.Variable;
import com.craftinginterpreters.lox.Expr.Visitor;

public class AstPrinter implements Visitor<String> {
	
	String print(Expr expr) {
		
		return expr.accept(this);
	}
	
	@Override
	public String visitBinaryExpr(Binary expr) {
		
		return parenthesize(expr.operator.lexeme, expr.left, expr.right);
	}
	
	@Override
	public String visitGroupingExpr(Grouping expr) {
		
		return parenthesize("group", expr.expression);
	}
	
	@Override
	public String visitLiteralExpr(Literal expr) {
		
		if(expr.value == null)
			return "nil";
		return expr.value.toString();
	}
	
	@Override
	public String visitUnaryExpr(Unary expr) {
		
		return parenthesize(expr.operator.lexeme, expr.right);
	}
	
	@Override
	public String visitAssignExpr(Assign expr) {
		
		return parenthesize(expr.name.lexeme + " =", expr.value);
	}
	
	@Override
	public String visitVariableExpr(Variable expr) {
		
		return expr.name.lexeme;
	}
	
	@Override
	public String visitLogicalExpr(Logical expr) {
		
		return parenthesize(expr.operator.lexeme, expr.left, expr.right);
	}
	
	@Override
	public String visitCallExpr(Call expr) {
		
		String args = "(";
		
		if(expr.arguments.size() > 0)
			args += expr.arguments.get(0);
		
		for(int i = 1; i < expr.arguments.size(); i++)
			args += ", " + expr.arguments.get(i);
		
		args += ")";
		
		return "(" + expr.callee.accept(this) + args + ")";
		
	}
	
	@Override
	public String visitGetExpr(Get expr) {
		
		return "(" + expr.object.accept(this) + "." + expr.name.lexeme + ")";
		
	}
	
	@Override
	public String visitSetExpr(Set expr) {
		
		return "(" + expr.object.accept(this) + "." + expr.name.lexeme + " = " + expr.value.accept(this) + ")";
	}
	
	@Override
	public String visitThisExpr(This expr) {
		
		return "this";
		
	}
	
	@Override
	public String visitSuperExpr(Super expr) {
		
		return "super." + expr.method.lexeme;
		
	}
	
	@Override
	public String visitLambdaExpr(Lambda expr) {
		
		return "lambda";
		
	}
	
	private String parenthesize(String name, Expr... exprs) {
		
		StringBuilder builder = new StringBuilder();
		
		builder.append("(").append(name);
		for(Expr expr : exprs)
			builder.append(" ").append(expr.accept(this));
		builder.append(")");
		
		return builder.toString();
		
	}
	
}
