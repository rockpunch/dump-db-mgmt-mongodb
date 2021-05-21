package io.dsub.discogsdata.batch.query;

public interface QueryBuilder<T> {

  String SPACE = " ";
  String DOT = ".";
  String AND = "AND";
  String COLON = ":";
  String SEMICOLON = ";";
  String OPEN_BRACE = "(";
  String CLOSE_BRACE = ")";
  String EQUALS = "=";
  String INSERT_INTO = "INSERT INTO";
  String VALUES = "VALUES";
  String ON_CONFLICT = "ON CONFLICT";
  String DO_UPDATE_SET = "DO UPDATE SET";
  String WHERE = "WHERE";
  String LAST_MODIFIED_FIELD = "lastModifiedAt";
  String LAST_MODIFIED_COLUMN = "last_modified_at";


  String getInsertQuery(Class<? extends T> targetClass);

  String getUpsertQuery(Class<? extends T> targetClass, boolean withId);
}