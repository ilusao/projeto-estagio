package com.example.estagioprojeto;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class Bancodedados extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "cartazista.db";
    private static final int DATABASE_VERSION = 1;

    private static final String TABLE_FAIXAS = "faixas";

    public Bancodedados(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_FAIXAS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "produto TEXT NOT NULL, " +
                "tipo_oferta TEXT NOT NULL, " +
                "preco_oferta REAL, " +
                "preco_normal REAL, " +
                "estado TEXT NOT NULL, " +
                "condicao TEXT NOT NULL, " +
                "comentario TEXT, " +
                "limite_cpf INTEGER NOT NULL DEFAULT 0, " +
                "data_criacao DATE DEFAULT CURRENT_DATE, " +
                "vezes_usada INTEGER DEFAULT 0" +
                ")";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_FAIXAS);
        onCreate(db);
    }

    // Inserir nova faixa
    public boolean inserirFaixa(String produto, String tipo_oferta, Double preco_oferta,
                                Double preco_normal, String estado, String condicao,
                                String comentario, boolean limite_cpf) {

        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put("produto", produto);
        values.put("tipo_oferta", tipo_oferta);
        values.put("preco_oferta", preco_oferta);
        values.put("preco_normal", preco_normal);
        values.put("estado", estado);
        values.put("condicao", condicao);
        values.put("comentario", comentario);
        values.put("limite_cpf", limite_cpf ? 1 : 0);

        long result = db.insert(TABLE_FAIXAS, null, values);
        return result != -1;
    }

    // Buscar todas as faixas
    public Cursor listarFaixas() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_FAIXAS, null);
    }

    // Calcular idade da faixa (em dias)
    public Cursor calcularIdadeFaixas() {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT id, produto, " +
                "ROUND(julianday('now') - julianday(data_criacao)) AS dias_desde_cadastro " +
                "FROM " + TABLE_FAIXAS;
        return db.rawQuery(query, null);
    }

    // Atualizar vezes usada
    public void incrementarUso(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("UPDATE " + TABLE_FAIXAS + " SET vezes_usada = vezes_usada + 1 WHERE id = ?",
                new Object[]{id});
    }

    public boolean deletarFaixa(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        int linhasAfetadas = db.delete("faixas", "id=?", new String[]{String.valueOf(id)});
        return linhasAfetadas > 0;
    }
}
