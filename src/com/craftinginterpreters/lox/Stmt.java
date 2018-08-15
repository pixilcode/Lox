package com.craftinginterpreters.lox;

import java.util.List;

public abstract class Stmt {
	interface Visitor<R> {
		R visitBlockStmt(Block stmt);
		R visitCatchStmt(Catch stmt);
		R visitClassStmt(Class stmt);
		R visitExitStmt(Exit stmt);
		R visitExpressionStmt(Expression stmt);
		R visitFunctionStmt(Function stmt);
		R visitIfStmt(If stmt);
		R visitImportStmt(Import stmt);
		R visitIncludeStmt(Include stmt);
		R visitPrintStmt(Print stmt);
		R visitReturnStmt(Return stmt);
		R visitThrowStmt(Throw stmt);
		R visitTryStmt(Try stmt);
		R visitVarStmt(Var stmt);
		R visitWhileStmt(While stmt);
	}
	static class Block extends Stmt {

		final List<Stmt> statements;

		Block(List<Stmt> statements) {
			this.statements = statements;
		}

		<R> R accept(Visitor<R> visitor) {
			return visitor.visitBlockStmt(this);
		}

	}
	static class Catch extends Stmt {

		final Token keyword;
		final List<Token> errors;
		final Token identifier;
		final Stmt body;

		Catch(Token keyword, List<Token> errors, Token identifier, Stmt body) {
			this.keyword = keyword;
			this.errors = errors;
			this.identifier = identifier;
			this.body = body;
		}

		<R> R accept(Visitor<R> visitor) {
			return visitor.visitCatchStmt(this);
		}

	}
	static class Class extends Stmt {

		final Token name;
		final Expr.Variable superclass;
		final List<Stmt.Function> methods;

		Class(Token name, Expr.Variable superclass, List<Stmt.Function> methods) {
			this.name = name;
			this.superclass = superclass;
			this.methods = methods;
		}

		<R> R accept(Visitor<R> visitor) {
			return visitor.visitClassStmt(this);
		}

	}
	static class Exit extends Stmt {

		final Token keyword;
		final Expr exitCode;

		Exit(Token keyword, Expr exitCode) {
			this.keyword = keyword;
			this.exitCode = exitCode;
		}

		<R> R accept(Visitor<R> visitor) {
			return visitor.visitExitStmt(this);
		}

	}
	static class Expression extends Stmt {

		final Expr expression;

		Expression(Expr expression) {
			this.expression = expression;
		}

		<R> R accept(Visitor<R> visitor) {
			return visitor.visitExpressionStmt(this);
		}

	}
	static class Function extends Stmt {

		final Token name;
		final List<Token> parameters;
		final List<Stmt> body;

		Function(Token name, List<Token> parameters, List<Stmt> body) {
			this.name = name;
			this.parameters = parameters;
			this.body = body;
		}

		<R> R accept(Visitor<R> visitor) {
			return visitor.visitFunctionStmt(this);
		}

	}
	static class If extends Stmt {

		final Token keyword;
		final Expr condition;
		final Stmt thenBranch;
		final Stmt elseBranch;

		If(Token keyword, Expr condition, Stmt thenBranch, Stmt elseBranch) {
			this.keyword = keyword;
			this.condition = condition;
			this.thenBranch = thenBranch;
			this.elseBranch = elseBranch;
		}

		<R> R accept(Visitor<R> visitor) {
			return visitor.visitIfStmt(this);
		}

	}
	static class Import extends Stmt {

		final Token keyword;
		final String file;
		final List<Stmt> body;

		Import(Token keyword, String file, List<Stmt> body) {
			this.keyword = keyword;
			this.file = file;
			this.body = body;
		}

		<R> R accept(Visitor<R> visitor) {
			return visitor.visitImportStmt(this);
		}

	}
	static class Include extends Stmt {

		final Token keyword;
		final String file;
		final List<Stmt> body;

		Include(Token keyword, String file, List<Stmt> body) {
			this.keyword = keyword;
			this.file = file;
			this.body = body;
		}

		<R> R accept(Visitor<R> visitor) {
			return visitor.visitIncludeStmt(this);
		}

	}
	static class Print extends Stmt {

		final Token keyword;
		final Expr expression;

		Print(Token keyword, Expr expression) {
			this.keyword = keyword;
			this.expression = expression;
		}

		<R> R accept(Visitor<R> visitor) {
			return visitor.visitPrintStmt(this);
		}

	}
	static class Return extends Stmt {

		final Token keyword;
		final Expr value;

		Return(Token keyword, Expr value) {
			this.keyword = keyword;
			this.value = value;
		}

		<R> R accept(Visitor<R> visitor) {
			return visitor.visitReturnStmt(this);
		}

	}
	static class Throw extends Stmt {

		final Token keyword;
		final Expr thrown;

		Throw(Token keyword, Expr thrown) {
			this.keyword = keyword;
			this.thrown = thrown;
		}

		<R> R accept(Visitor<R> visitor) {
			return visitor.visitThrowStmt(this);
		}

	}
	static class Try extends Stmt {

		final Token keyword;
		final Stmt body;
		final List<Stmt.Catch> catches;
		final Stmt finallyStmt;

		Try(Token keyword, Stmt body, List<Stmt.Catch> catches, Stmt finallyStmt) {
			this.keyword = keyword;
			this.body = body;
			this.catches = catches;
			this.finallyStmt = finallyStmt;
		}

		<R> R accept(Visitor<R> visitor) {
			return visitor.visitTryStmt(this);
		}

	}
	static class Var extends Stmt {

		final Token name;
		final Expr initializer;

		Var(Token name, Expr initializer) {
			this.name = name;
			this.initializer = initializer;
		}

		<R> R accept(Visitor<R> visitor) {
			return visitor.visitVarStmt(this);
		}

	}
	static class While extends Stmt {

		final Token keyword;
		final Expr condition;
		final Stmt body;

		While(Token keyword, Expr condition, Stmt body) {
			this.keyword = keyword;
			this.condition = condition;
			this.body = body;
		}

		<R> R accept(Visitor<R> visitor) {
			return visitor.visitWhileStmt(this);
		}

	}

	abstract <R> R accept(Visitor<R> visitor);
}
