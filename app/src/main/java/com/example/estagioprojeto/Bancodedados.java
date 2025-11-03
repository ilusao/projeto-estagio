package com.example.estagioprojeto;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.util.Log;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.Normalizer;
import java.util.ArrayList;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.time.DayOfWeek;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import java.io.File;
import java.io.FileWriter;
import java.io.BufferedWriter;


public class Bancodedados extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "cartazista.db";
    private static final int DATABASE_VERSION = 5;

    private static final String TABLE_FAIXAS = "faixas";

    private Context context;

    public Bancodedados(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_FAIXAS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "codigo_faixa INTEGER NOT NULL, " +
                "produto TEXT NOT NULL, " +
                "tipo_oferta TEXT NOT NULL, " +
                "preco_oferta REAL, " +
                "preco_normal REAL, " +
                "estado TEXT NOT NULL, " +
                "condicao TEXT NOT NULL, " +
                "comentario TEXT, " +
                "limite_cpf INTEGER NOT NULL DEFAULT 0, " +
                "data_criacao DATE DEFAULT CURRENT_DATE, " +
                "vezes_usada INTEGER DEFAULT 0, " +
                "usando INTEGER DEFAULT 0, " +
                "dias_uso INTEGER DEFAULT 0, " +
                "data_inicio_uso DATE" +
                ")";
        db.execSQL(createTable);
        popularBancoDeFaixasInterno(db);
        criarTabelaProdutosCartazista(db);
        popularProdutosCartazista(db);
        criarTabelaPedidos(db);
        criarTabelaUsoProdutos(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_FAIXAS);
        onCreate(db);
    }

    // Atualizar uso automático (verifica se dias de uso acabaram)
    public void atualizarUsoAutomatico() {
        SQLiteDatabase db = this.getWritableDatabase();
        String query = "SELECT codigo, data_inicio_uso, dias_uso FROM " + TABLE_FAIXAS + " WHERE usando=1";
        Cursor cursor = db.rawQuery(query, null);

        if (cursor.moveToFirst()) {
            do {
                String dataInicio = cursor.getString(cursor.getColumnIndexOrThrow("data_inicio_uso"));
                int diasUso = cursor.getInt(cursor.getColumnIndexOrThrow("dias_uso"));
                int codigo = cursor.getInt(cursor.getColumnIndexOrThrow("codigo")); // <-- pega o código

                if (dataInicio != null) {
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
                    try {
                        java.util.Date inicio = sdf.parse(dataInicio);
                        java.util.Date hoje = new java.util.Date();
                        long diff = hoje.getTime() - inicio.getTime();
                        long diasPassados = diff / (1000L * 60 * 60 * 24);

                        if (diasPassados >= diasUso) {
                            cancelarUso(codigo);
                        }
                    } catch (java.text.ParseException e) {
                        e.printStackTrace();
                    }
                }
            } while (cursor.moveToNext());
        }
        cursor.close();
    }


    public int inserirFaixaComDuplicataOpcional(int codigoFaixa, String produto, String tipo_oferta, String preco_oferta_str,
                                                String preco_normal_str, String estado, String condicao,
                                                String comentario, boolean limite_cpf, boolean permitirDuplicata) {
        try {
            SQLiteDatabase db = this.getWritableDatabase();

            // Converter preços para double
            Double precoOferta = null;
            Double precoNormal = null;

            if (preco_oferta_str != null && !preco_oferta_str.trim().isEmpty()) {
                try {
                    precoOferta = Double.parseDouble(preco_oferta_str.replace(",", "."));
                } catch (NumberFormatException e) {
                    Log.e("DEBUG_DB", "Preço oferta inválido: " + preco_oferta_str);
                    return 0;
                }
            }

            if (preco_normal_str != null && !preco_normal_str.trim().isEmpty()) {
                try {
                    precoNormal = Double.parseDouble(preco_normal_str.replace(",", "."));
                } catch (NumberFormatException e) {
                    Log.e("DEBUG_DB", "Preço normal inválido: " + preco_normal_str);
                    return 0;
                }
            }

            // 🔹 Gerar código de faixa automaticamente se for 0 ou inválido
            if (codigoFaixa <= 0) {
                Cursor cursor = db.rawQuery("SELECT codigo_faixa FROM " + TABLE_FAIXAS + " ORDER BY codigo_faixa ASC", null);
                int novoCodigo = 1;
                while (cursor.moveToNext()) {
                    int existente = cursor.getInt(0);
                    if (existente == novoCodigo) {
                        novoCodigo++;
                    } else if (existente > novoCodigo) {
                        break; // encontrou o primeiro espaço vazio
                    }
                }
                cursor.close();

                if (novoCodigo > 999) {
                    Log.e("DEBUG_DB", "Limite de códigos de faixa atingido!");
                    return 0;
                }
                codigoFaixa = novoCodigo;
            }

            // Checagem de duplicata
            if (!permitirDuplicata) {
                Cursor cursor = db.rawQuery(
                        "SELECT id FROM " + TABLE_FAIXAS + " WHERE codigo_faixa=? OR (produto=? AND tipo_oferta=? AND preco_oferta=? AND preco_normal=? " +
                                "AND estado=? AND condicao=? AND limite_cpf=?)",
                        new String[]{
                                String.valueOf(codigoFaixa),
                                produto,
                                tipo_oferta,
                                precoOferta == null ? "0" : precoOferta.toString(),
                                precoNormal == null ? "0" : precoNormal.toString(),
                                estado,
                                condicao,
                                limite_cpf ? "1" : "0"
                        });
                boolean existe = cursor.moveToFirst();
                cursor.close();
                if (existe) {
                    return -2;
                }
            }

            // Inserção no banco
            ContentValues values = new ContentValues();
            values.put("codigo_faixa", codigoFaixa);
            values.put("produto", produto);
            values.put("tipo_oferta", tipo_oferta);
            values.put("preco_oferta", precoOferta);
            values.put("preco_normal", precoNormal);
            values.put("estado", estado);
            values.put("condicao", condicao);
            values.put("comentario", comentario);
            values.put("limite_cpf", limite_cpf ? 1 : 0);

            long id = db.insert(TABLE_FAIXAS, null, values);
            Log.d("DEBUG_DB", "Insert result: " + id + " | código_faixa=" + codigoFaixa);

            if (id == -1) return 0;

            // 🔹 Log para confirmar faixa cadastrada
            Log.i("DEBUG_DB", "Faixa cadastrada com sucesso! Código da faixa: " + codigoFaixa);

            return codigoFaixa;

        } catch (Exception e) {
            Log.e("DEBUG_DB", "Erro ao inserir faixa", e);
            return 0;
        }
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

    public boolean deletarFaixaPorCodigo(int codigoFaixa) {
        SQLiteDatabase db = this.getWritableDatabase();
        int linhasAfetadas = db.delete(TABLE_FAIXAS, "codigo_faixa=?", new String[]{String.valueOf(codigoFaixa)});
        return linhasAfetadas > 0;
    }

    public boolean editarFaixa(int id, String produto, String tipoOferta,
                               Double precoOferta, Double precoNormal,
                               String estado, String condicao,
                               String comentario, int limiteCpf) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("produto", produto);
        values.put("tipo_oferta", tipoOferta);

        if (precoOferta != null) {
            values.put("preco_oferta", precoOferta);
        } else {
            values.putNull("preco_oferta");
        }

        if (precoNormal != null) {
            values.put("preco_normal", precoNormal);
        } else {
            values.putNull("preco_normal");
        }

        values.put("estado", estado);
        values.put("condicao", condicao);
        values.put("comentario", comentario);
        values.put("limite_cpf", limiteCpf);

        int linhasAfetadas = db.update(TABLE_FAIXAS, values, "codigo_faixa = ?", new String[]{String.valueOf(id)});
        return linhasAfetadas > 0;
    }

    // Iniciar uso da faixa
    public boolean iniciarUso(int codigo, int dias) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("usando", 1);
        values.put("dias_uso", dias);
        values.put("data_inicio_uso", java.time.LocalDate.now().toString());
        int linhas = db.update(TABLE_FAIXAS, values, "codigo_faixa=?", new String[]{String.valueOf(codigo)});
        return linhas > 0;
    }

    // Cancelar uso da faixa
    public boolean cancelarUso(int codigo) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("usando", 0);
        values.put("dias_uso", 0);
        values.putNull("data_inicio_uso");
        int linhas = db.update(TABLE_FAIXAS, values, "codigo_faixa=?", new String[]{String.valueOf(codigo)});
        return linhas > 0;
    }

    private void popularBancoDeFaixasInterno(SQLiteDatabase db) {
        try {
            InputStream is = context.getAssets().open("faixa.txt");
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, "UTF-8")
            );
            String linha;
            while ((linha = reader.readLine()) != null) {
                String[] partes = linha.split(";");
                if (partes.length >= 8) {
                    ContentValues values = new ContentValues();

                    // Código de faixa numérico ou 0
                    int codigoFaixa = 0;
                    try {
                        codigoFaixa = Integer.parseInt(partes[0].trim());
                    } catch (NumberFormatException e) {
                        codigoFaixa = 0; // será gerado automaticamente se necessário
                    }
                    values.put("codigo_faixa", codigoFaixa);

                    values.put("produto", partes[1]);
                    values.put("tipo_oferta", partes[2]);

                    // Preços
                    try {
                        values.put("preco_oferta", Double.parseDouble(partes[3].replace(",", ".")));
                    } catch (Exception e) { values.putNull("preco_oferta"); }
                    try {
                        values.put("preco_normal", Double.parseDouble(partes[4].replace(",", ".")));
                    } catch (Exception e) { values.putNull("preco_normal"); }

                    values.put("estado", partes[5]);
                    values.put("condicao", partes[6]);
                    values.put("comentario", partes[7]);

                    // Tratamento correto do limite_cpf
                    int limiteCpf = 0; // padrão = 0
                    if (partes.length > 8) {
                        String limiteStr = partes[8].trim(); // antes era partes[7], agora é 8
                        if (limiteStr.equalsIgnoreCase("Sim") || limiteStr.equals("1")) {
                            limiteCpf = 1;
                        }
                    }
                    values.put("limite_cpf", limiteCpf);

                    db.insert(TABLE_FAIXAS, null, values);
                }
            }
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }




    public void popularBancoDeFaixas() {
        SQLiteDatabase db = this.getWritableDatabase();
        popularBancoDeFaixasInterno(db);
    }

    public void atualizarEstado(int id, String novoEstado) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("estado", novoEstado);
        db.update("faixas", values, "id = ?", new String[]{String.valueOf(id)});
    }


    // Busca faixas por nome do produto (ou parte dele)
    public List<Faixa> pesquisarFaixa(String texto) {
        List<Faixa> resultado = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();

        String[] palavras = texto.trim().toLowerCase().split("\\s+");

        StringBuilder where = new StringBuilder();
        List<String> args = new ArrayList<>();

        for (String palavra : palavras) {
            if (where.length() > 0) where.append(" AND ");
            where.append("LOWER(produto) LIKE ?");
            args.add("%" + palavra + "%");
        }

        Cursor cursor = db.query(TABLE_FAIXAS,
                null,
                where.toString(),
                args.toArray(new String[0]),
                null, null, null);

        while (cursor.moveToNext()) {
            Faixa f = new Faixa();
            f.setCodigoFaixa(cursor.getInt(cursor.getColumnIndexOrThrow("codigo_faixa")));
            f.setProduto(cursor.getString(cursor.getColumnIndexOrThrow("produto")));
            f.setTipoOferta(cursor.getString(cursor.getColumnIndexOrThrow("tipo_oferta")));
            f.setPrecoOferta(cursor.getDouble(cursor.getColumnIndexOrThrow("preco_oferta")));
            f.setPrecoNormal(cursor.getDouble(cursor.getColumnIndexOrThrow("preco_normal")));
            f.setEstado(cursor.getString(cursor.getColumnIndexOrThrow("estado")));
            f.setCondicao(cursor.getString(cursor.getColumnIndexOrThrow("condicao")));
            f.setComentario(cursor.getString(cursor.getColumnIndexOrThrow("comentario")));
            f.setLimiteCpf(cursor.getInt(cursor.getColumnIndexOrThrow("limite_cpf")));
            f.setUsando(cursor.getInt(cursor.getColumnIndexOrThrow("usando")));
            f.setDiasUso(cursor.getInt(cursor.getColumnIndexOrThrow("dias_uso")));
            f.setDiasRestantes(0); // opcional, calcular depois
            f.setVezesUsada(cursor.getInt(cursor.getColumnIndexOrThrow("vezes_usada")));
            f.setDataCadastro(cursor.getString(cursor.getColumnIndexOrThrow("data_criacao")));
            f.setDataInicioUso(cursor.getString(cursor.getColumnIndexOrThrow("data_inicio_uso")));
            f.setTempoFaixa(calcularTempoFaixa(cursor.getString(cursor.getColumnIndexOrThrow("data_criacao"))));

            resultado.add(f);
        }

        cursor.close();
        return resultado;
    }

    public Cursor filtrarFaixas(String tipoOferta, String estado, String condicao, String limiteCpf, String emUso) {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT * FROM " + TABLE_FAIXAS + " WHERE 1=1";
        ArrayList<String> args = new ArrayList<>();

        Log.d("FiltroLogs", "===== FILTRO RECEBIDO =====");
        Log.d("FiltroLogs", "tipoOferta: " + tipoOferta);
        Log.d("FiltroLogs", "estado: " + estado);
        Log.d("FiltroLogs", "condicao: " + condicao);
        Log.d("FiltroLogs", "limiteCpf: " + limiteCpf);
        Log.d("FiltroLogs", "emUso: " + emUso);

        if (tipoOferta != null && !tipoOferta.isEmpty()) {
            tipoOferta = removerAcentos(tipoOferta).toUpperCase();
            query += " AND UPPER(TRIM(tipo_oferta)) = ?";
            args.add(tipoOferta);
        }

        if (estado != null && !estado.isEmpty()) {
            estado = removerAcentos(estado).toUpperCase();
            query += " AND UPPER(TRIM(estado)) = ?";
            args.add(estado);
        }

        if (condicao != null && !condicao.isEmpty()) {
            condicao = removerAcentos(condicao).toUpperCase();
            query += " AND UPPER(TRIM(condicao)) = ?";
            args.add(condicao);
        }
        if (limiteCpf != null && !limiteCpf.isEmpty()) {
            query += " AND limite_cpf = ?";
            args.add(limiteCpf);
        }
        if (emUso != null && !emUso.isEmpty()) {
            query += " AND usando = ?";
            args.add(emUso);
        }

        Log.d("FiltroLogs", "SQL gerado: " + query);
        Log.d("FiltroLogs", "Args: " + args.toString());

        Cursor cursor = db.rawQuery(query, args.toArray(new String[0]));
        Log.d("FiltroLogs", "Quantidade de resultados: " + cursor.getCount());
        return cursor;
    }

    public static String removerAcentos(String str) {
        return Normalizer.normalize(str, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }

    public List<Faixa> listarFaixasComoObjetos() {
        atualizarUsoAutomatico(); // garante que as faixas expiradas já foram resetadas

        List<Faixa> faixas = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_FAIXAS, null);

        if (cursor.moveToFirst()) {
            do {
                int id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
                int codigoFaixa = cursor.getInt(cursor.getColumnIndexOrThrow("codigo_faixa"));
                String produto = cursor.getString(cursor.getColumnIndexOrThrow("produto"));
                String tipoOferta = cursor.getString(cursor.getColumnIndexOrThrow("tipo_oferta"));
                double precoOferta = cursor.getDouble(cursor.getColumnIndexOrThrow("preco_oferta"));
                double precoNormal = cursor.getDouble(cursor.getColumnIndexOrThrow("preco_normal"));
                String estado = cursor.getString(cursor.getColumnIndexOrThrow("estado"));
                String condicao = cursor.getString(cursor.getColumnIndexOrThrow("condicao"));
                String comentario = cursor.getString(cursor.getColumnIndexOrThrow("comentario"));
                int limiteCpf = cursor.getInt(cursor.getColumnIndexOrThrow("limite_cpf"));
                int usando = cursor.getInt(cursor.getColumnIndexOrThrow("usando"));
                int diasUso = cursor.getInt(cursor.getColumnIndexOrThrow("dias_uso"));
                String dataInicioStr = cursor.getString(cursor.getColumnIndexOrThrow("data_inicio_uso"));

                int diasRestantes = 0;

                if (usando == 1 && dataInicioStr != null) {
                    LocalDate dataInicio = LocalDate.parse(dataInicioStr);
                    long passados = ChronoUnit.DAYS.between(dataInicio, LocalDate.now());

                    if (passados >= diasUso) {
                        cancelarUso(id); // cancela no banco
                        usando = 0;
                        diasUso = 0;
                        diasRestantes = 0;
                    } else {
                        diasRestantes = (int)(diasUso - passados);
                    }
                }

                Faixa f = new Faixa(produto, tipoOferta, precoOferta, precoNormal,
                        estado, condicao, comentario, limiteCpf);
                f.setId(id);
                f.setCodigoFaixa(codigoFaixa);
                f.setUsando(usando);
                f.setDiasUso(diasUso);
                f.setDiasRestantes(diasRestantes);

                faixas.add(f);
            } while (cursor.moveToNext());
        }
        cursor.close();
        return faixas;
    }

    public boolean exportarFaixasParaTxt(File destino) {
        try {
            FileWriter writer = new FileWriter(destino, false); // sobrescreve
            BufferedWriter bw = new BufferedWriter(writer);

            SQLiteDatabase db = this.getReadableDatabase();
            Cursor cursor = db.rawQuery(
                    "SELECT id,codigo_faixa, produto, tipo_oferta, preco_oferta, preco_normal, estado, condicao, comentario, limite_cpf FROM " + TABLE_FAIXAS,
                    null
            );

            while (cursor.moveToNext()) {
                int id = cursor.getInt(0);
                int codigo_faixa = cursor.getInt(1);
                String produto = cursor.getString(2);
                String tipoOferta = cursor.getString(3);
                String precoOferta = cursor.getString(4);
                String precoNormal = cursor.getString(5);
                String estado = cursor.getString(6);
                String condicao = cursor.getString(7);
                String comentario = cursor.getString(8);
                int limiteCpf = cursor.getInt(9);

                String linha =  id + ";" + codigo_faixa + ";" + produto + ";" + tipoOferta + ";" + precoOferta + ";" +
                        precoNormal + ";" + estado + ";" + condicao + ";" +
                        comentario + ";" + (limiteCpf == 1 ? "Sim" : "Não");

                bw.write(linha);
                bw.newLine();
            }

            cursor.close();
            bw.close();
            writer.close();
            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private String calcularTempoFaixa(String dataCriacao) {
        if (dataCriacao == null) return "0 dias";
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            Date inicio = sdf.parse(dataCriacao);
            Date hoje = new Date();
            long diff = hoje.getTime() - inicio.getTime();
            long dias = diff / (1000L * 60 * 60 * 24);

            long anos = dias / 360;
            dias %= 360;
            long meses = dias / 30;
            dias %= 30;
            long semanas = dias / 7;
            dias %= 7;

            StringBuilder sb = new StringBuilder();
            if (anos > 0) sb.append(anos).append(anos == 1 ? " ano " : " anos ");
            if (meses > 0) sb.append(meses).append(meses == 1 ? " mês " : " meses ");
            if (semanas > 0) sb.append(semanas).append(semanas == 1 ? " semana " : " semanas ");
            if (dias > 0) sb.append(dias).append(dias == 1 ? " dia" : " dias");

            String resultado = sb.toString().trim();
            String[] partes = resultado.split(" ");
            if (partes.length > 4) {
                resultado = partes[0] + " " + partes[1] + " e " + partes[2] + " " + partes[3];
            }
            return resultado.isEmpty() ? "0 dias" : resultado;

        } catch (Exception e) {
            return "0 dias";
        }
    }


    // -------------------------------------------graficos--graficos---------------------------------------------------------------//
    // -------------------------------------------graficos--graficos---------------------------------------------------------------//
    // -------------------------------------------graficos--graficos---------------------------------------------------------------//
    public int contarFaixasParadasUltimoMes() {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT COUNT(*) AS total " +
                "FROM faixas " +
                "WHERE (vezes_usada = 0 AND julianday('now') - julianday(data_criacao) >= 30) " +
                "OR (data_inicio_uso IS NOT NULL AND julianday('now') - julianday(data_inicio_uso) >= 30 AND usando = 0)";
        Cursor cursor = db.rawQuery(query, null);
        int total = 0;
        if (cursor.moveToFirst()) {
            total = cursor.getInt(cursor.getColumnIndexOrThrow("total"));
        }
        cursor.close();
        return total;
    }

    public List<Faixa> getFaixasParadasUltimoMes() {
        List<Faixa> lista = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT * FROM faixas WHERE (vezes_usada = 0 AND julianday('now') - julianday(data_criacao) >= 30) " +
                "OR (data_inicio_uso IS NOT NULL AND julianday('now') - julianday(data_inicio_uso) >= 30 AND usando = 0)";
        Cursor cursor = db.rawQuery(query, null);
        while(cursor.moveToNext()){
            Faixa f = new Faixa(
                    cursor.getString(cursor.getColumnIndexOrThrow("produto")),
                    cursor.getString(cursor.getColumnIndexOrThrow("tipo_oferta")),
                    cursor.getDouble(cursor.getColumnIndexOrThrow("preco_oferta")),
                    cursor.getDouble(cursor.getColumnIndexOrThrow("preco_normal")),
                    cursor.getString(cursor.getColumnIndexOrThrow("estado")),
                    cursor.getString(cursor.getColumnIndexOrThrow("condicao")),
                    cursor.getString(cursor.getColumnIndexOrThrow("comentario")),
                    cursor.getInt(cursor.getColumnIndexOrThrow("limite_cpf"))
            );
            f.setVezesUsada(cursor.getInt(cursor.getColumnIndexOrThrow("vezes_usada")));
            f.setCodigoFaixa(cursor.getInt(cursor.getColumnIndexOrThrow("codigo_faixa")));
            lista.add(f);
        }
        cursor.close();
        return lista;
    }

    public List<Faixa> getFaixasVelhas() {
        List<Faixa> lista = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        // Usando COLLATE NOCASE para ignorar maiúsculas/minúsculas
        Cursor cursor = db.rawQuery(
                "SELECT * FROM faixas WHERE estado = ? COLLATE NOCASE ORDER BY data_criacao ASC",
                new String[]{"velha"}
        );

        if (cursor.moveToFirst()) {
            do {
                Faixa f = new Faixa();
                f.setCodigoFaixa(cursor.getInt(cursor.getColumnIndexOrThrow("codigo_faixa")));
                f.setProduto(cursor.getString(cursor.getColumnIndexOrThrow("produto")));
                f.setEstado(cursor.getString(cursor.getColumnIndexOrThrow("estado")));
                f.setVezesUsada(cursor.getInt(cursor.getColumnIndexOrThrow("vezes_usada")));
                f.setUsando(cursor.getInt(cursor.getColumnIndexOrThrow("usando")));
                f.setDiasUso(cursor.getInt(cursor.getColumnIndexOrThrow("dias_uso")));
                f.setDataCadastro(cursor.getString(cursor.getColumnIndexOrThrow("data_criacao")));
                lista.add(f);
            } while (cursor.moveToNext());
        }
        cursor.close();
        return lista;
    }

    public List<Faixa> getTop10Faixas() {
        List<Faixa> faixas = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM faixas ORDER BY vezes_usada DESC LIMIT 10", null);

        if (cursor.moveToFirst()) {
            do {
                Faixa f = new Faixa();
                f.setCodigoFaixa(cursor.getInt(cursor.getColumnIndex("codigo_faixa")));
                f.setProduto(cursor.getString(cursor.getColumnIndex("produto")));
                f.setEstado(cursor.getString(cursor.getColumnIndex("estado")));
                f.setVezesUsada(cursor.getInt(cursor.getColumnIndex("vezes_usada")));
                f.setDataCadastro(cursor.getString(cursor.getColumnIndex("data_criacao")));
                faixas.add(f);
            } while (cursor.moveToNext());
        }
        cursor.close();
        return faixas;
    }

    public List<Faixa> getFaixasParaLixo() {
        List<Faixa> faixas = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.rawQuery("SELECT * FROM faixas WHERE LOWER(estado) = 'velha'", null);

        if (cursor.moveToFirst()) {
            do {
                Faixa f = new Faixa();
                f.setCodigoFaixa(cursor.getInt(cursor.getColumnIndexOrThrow("codigo_faixa")));
                f.setProduto(cursor.getString(cursor.getColumnIndexOrThrow("produto")));
                f.setEstado(cursor.getString(cursor.getColumnIndexOrThrow("estado")));
                f.setVezesUsada(cursor.getInt(cursor.getColumnIndexOrThrow("vezes_usada")));
                f.setCondicao(cursor.getString(cursor.getColumnIndexOrThrow("condicao")));
                f.setDataCadastro(cursor.getString(cursor.getColumnIndexOrThrow("data_criacao")));

                // Calcula dias desde a última utilização ou criação
                int diasSemUso = 0;
                String dataInicioUso = cursor.getString(cursor.getColumnIndex("data_inicio_uso"));
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                    Date inicio = dataInicioUso != null ? sdf.parse(dataInicioUso) : sdf.parse(f.getDataCadastro());
                    long diff = new Date().getTime() - inicio.getTime();
                    diasSemUso = (int)(diff / (1000L * 60 * 60 * 24));
                } catch (Exception e) { e.printStackTrace(); }

                // Adiciona se for lixo real ou sugestão
                if ((f.getCondicao() != null && f.getCondicao().equalsIgnoreCase("ruim")) || diasSemUso > 30) {
                    faixas.add(f);
                }

            } while (cursor.moveToNext());
        }

        cursor.close();
        return faixas;
    }

    public List<Faixa> getFaixasPorSemana() {
        SQLiteDatabase db = this.getReadableDatabase();
        List<Faixa> faixasSemana = new ArrayList<>();


        atualizarUsoAutomatico();

        LocalDate hoje = LocalDate.now();
        LocalDate inicioSemana = hoje.with(DayOfWeek.MONDAY);
        LocalDate inicioSemanaPassada = inicioSemana.minusWeeks(1);
        LocalDate fimSemanaPassada = inicioSemana.minusDays(1);

        // Semana atual
        Cursor cursorAtual = db.rawQuery(
                "SELECT * FROM faixas WHERE data_criacao BETWEEN ? AND ?",
                new String[]{inicioSemana.toString(), hoje.toString()}
        );
        if (cursorAtual.moveToFirst()) {
            do {
                Faixa f = new Faixa();
                f.setCodigoFaixa(cursorAtual.getInt(cursorAtual.getColumnIndexOrThrow("codigo_faixa")));
                f.setProduto(cursorAtual.getString(cursorAtual.getColumnIndex("produto")));
                f.setDataCadastro(cursorAtual.getString(cursorAtual.getColumnIndex("data_criacao")));
                faixasSemana.add(f);
            } while (cursorAtual.moveToNext());
        }
        cursorAtual.close();

        // Semana passada
        Cursor cursorPassada = db.rawQuery(
                "SELECT * FROM faixas WHERE data_criacao BETWEEN ? AND ?",
                new String[]{inicioSemanaPassada.toString(), fimSemanaPassada.toString()}
        );
        if (cursorPassada.moveToFirst()) {
            do {
                Faixa f = new Faixa();
                f.setCodigoFaixa(cursorPassada.getInt(cursorPassada.getColumnIndexOrThrow("codigo_faixa")));
                f.setProduto(cursorPassada.getString(cursorPassada.getColumnIndex("produto")));
                f.setDataCadastro(cursorPassada.getString(cursorPassada.getColumnIndex("data_criacao")));
                faixasSemana.add(f);
            } while (cursorPassada.moveToNext());
        }
        cursorPassada.close();

        return faixasSemana;
    }

    // -------------------------------------------produtos--produtos---------------------------------------------------------------//
    // -------------------------------------------produtos--produtos---------------------------------------------------------------//
    // -------------------------------------------produtos--produtos---------------------------------------------------------------//


    public Cursor listarProdutosCartazista() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.query("produtos_cartazista", null, null, null, null, null, "descricao ASC");
    }
    // Cria tabela produtos_cartazista
    private void criarTabelaProdutosCartazista(SQLiteDatabase db) {
        String createTableProdutos = "CREATE TABLE IF NOT EXISTS produtos_cartazista (" +
                "codigo INTEGER PRIMARY KEY, " +
                "descricao TEXT NOT NULL, " +
                "categoria TEXT NOT NULL, " +
                "preco REAL NOT NULL, " +
                "embalagem TEXT NOT NULL, " +
                "qtd_por_embalagem REAL NOT NULL" +
                ")";
        db.execSQL(createTableProdutos);
    }

    public void popularProdutosCartazista(SQLiteDatabase db) {
        String[] inserts = {
                "INSERT OR IGNORE INTO produtos_cartazista VALUES (717916, 'CARTAZ 21X30 CONFIRA 2X(15X21) IMP OFF 120', 'Cartaz', 0.260, 'Pacote c/ 100 unidades', 100)",
                "INSERT OR IGNORE INTO produtos_cartazista VALUES (717940, 'CARTAZ 21X30 CONFIRA TOPO IMP OFF 120', 'Cartaz', 0.260, 'Pacote c/ 100 unidades', 100)",
                "INSERT OR IGNORE INTO produtos_cartazista VALUES (717894, 'CARTAZ 21X30 IMP OFF 120', 'Cartaz', 0.390, 'Pacote c/ 100 unidades', 100)",
                "INSERT OR IGNORE INTO produtos_cartazista VALUES (717908, 'CARTAZ 21X30 OFERTA 2X (15X21) IMP OFF 120', 'Cartaz', 0.290, 'Pacote c/ 100 unidades', 100)",
                "INSERT OR IGNORE INTO produtos_cartazista VALUES (717932, 'CARTAZ 21X30 OFERTA TOPO IMP OFF 120', 'Cartaz', 0.290, 'Pacote c/ 100 unidades', 100)",
                "INSERT OR IGNORE INTO produtos_cartazista VALUES (717959, 'CARTAZ 30X66 LISO IMP OFF 120', 'Cartaz', 1.740, 'Pacote c/ 100 unidades', 100)",
                "INSERT OR IGNORE INTO produtos_cartazista VALUES (717860, 'CARTAZ 42X30 A3 IMP OFF 120', 'Cartaz', 0.490, 'Pacote c/ 100 unidades', 100)",
                "INSERT OR IGNORE INTO produtos_cartazista VALUES (717886, 'CARTAZ 42X30 CONFIRA TOPO IMP OFF 120', 'Cartaz', 0.380, 'Pacote c/ 100 unidades', 100)",
                "INSERT OR IGNORE INTO produtos_cartazista VALUES (717878, 'CARTAZ 42X30 OFERTA TOPO IMP OFF 120', 'Cartaz', 0.400, 'Pacote c/ 100 unidades', 100)",
                "INSERT OR IGNORE INTO produtos_cartazista VALUES (717967, 'CARTAZ 66X96 LISO IMP OFF 120', 'Cartaz', 1.610, 'Pacote c/ 100 unidades', 100)",
                "INSERT OR IGNORE INTO produtos_cartazista VALUES (142220, 'MAT ESCRIT GRAMPO ROCAMA 106/6', 'Material Escrit', 14.700, 'Pacote c/ 3500 unidades', 3500)",
                "INSERT OR IGNORE INTO produtos_cartazista VALUES (142905, 'MAT ESCRIT COLA BASTAO 20G', 'Material Escrit', 9.270, 'Unidade', 1)",
                "INSERT OR IGNORE INTO produtos_cartazista VALUES (434051, 'CARTAZISTA PAPEL CARTAO AMARELO 66X96', 'Papel', 239.520, 'Pacote c/ 100 unidades', 100)",
                "INSERT OR IGNORE INTO produtos_cartazista VALUES (143278, 'PINCEL ATOM 1100 CARBY PRETO', 'Pincel', 3.960, 'Unidade', 1)",
                "INSERT OR IGNORE INTO produtos_cartazista VALUES (143294, 'PINCEL ATOM 1100 CARBY VERM', 'Pincel', 3.950, 'Unidade', 1)",
                "INSERT OR IGNORE INTO produtos_cartazista VALUES (143324, 'PINCEL ATOM 850 PILOT PRETO', 'Pincel', 3.120, 'Unidade', 1)",
                "INSERT OR IGNORE INTO produtos_cartazista VALUES (143332, 'PINCEL ATOM 850 PILOT VERM', 'Pincel', 3.120, 'Unidade', 1)",
                "INSERT OR IGNORE INTO produtos_cartazista VALUES (141550, 'CARTAZISTA TINTA P/PINCEL PRETO', 'Tinta', 54.850, 'Unidade (Frasco/Lata)', 1)",
                "INSERT OR IGNORE INTO produtos_cartazista VALUES (141569, 'CARTAZISTA TINTA P/PINCEL VERMELHO', 'Tinta', 54.870, 'Unidade (Frasco/Lata)', 1)",
                "INSERT OR IGNORE INTO produtos_cartazista VALUES (497924, 'SUPRIMENTO FEIXE DE MADEIRA P/ CARTAZ 1M', 'Suprimento', 151.990, 'Pacote c/ 100 unidades', 100)",
                "INSERT OR IGNORE INTO produtos_cartazista VALUES (377732, 'SUPRIMENTO BOBINA PLAST AMARELA CARTAZ', 'Suprimento', 21.960, 'Rolo de 20 KG', 20)",
                "INSERT OR IGNORE INTO produtos_cartazista VALUES (143464, 'SUPRIMENTO BOBINA PLAST PERS.GOND. SV', 'Suprimento', 17.200, 'Rolo de 20 KG (Estimativa)', 20)",
                "INSERT OR IGNORE INTO produtos_cartazista VALUES (376973, 'SUPRIMENTO TECIDOS JUTA P9', 'Suprimento', 10.000, 'Rolo de 50 metros', 50)",
                "INSERT OR IGNORE INTO produtos_cartazista VALUES (599530, 'SUPRIMENTO SUPORTE CARTAZ PVC 15X21', 'Suprimento', 6.140, 'Unidade', 1)",
                "INSERT OR IGNORE INTO produtos_cartazista VALUES (145971, 'SUPRIMENTO ABRAÇADEIRA NYLON 10 X 2,5', 'Suprimento', 5.340, 'Pacote c/ 100 unidades', 100)",
                "INSERT OR IGNORE INTO produtos_cartazista VALUES (145980, 'SUPRIMENTO ABRAÇADEIRA NYLON 15 X 3,4', 'Suprimento', 12.000, 'Pacote c/ 100 unidades', 100)",
                "INSERT OR IGNORE INTO produtos_cartazista VALUES (571725, 'SUPRIMENTO ABRAÇADEIRA NYLON 30 X 3,6', 'Suprimento', 15.980, 'Pacote c/ 100 unidades', 100)",
                "INSERT OR IGNORE INTO produtos_cartazista VALUES (836427, 'SUPRIMENTO PEG BOARDS C/TRAVA 75', 'Suprimento', 0.520, 'Unidade', 1)"
        };

        for (String sql : inserts) {
            db.execSQL(sql);
        }
    }

    private void criarTabelaPedidos(SQLiteDatabase db) {
        String createTablePedidos = "CREATE TABLE IF NOT EXISTS pedidos (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "id_pedido INTEGER NOT NULL, " +
                "codigo_produto INTEGER NOT NULL, " +
                "quantidade INTEGER NOT NULL, " +
                "preco REAL NOT NULL, " +
                "data_pedido TEXT DEFAULT (datetime('now','localtime')), " +
                "FOREIGN KEY (codigo_produto) REFERENCES produtos_cartazista(codigo)" +
                ")";
        db.execSQL(createTablePedidos);
    }

    // Inserir pedido com chave estrangeira
    public void inserirPedido(int idPedido, int codigoProduto, int quantidade, double preco) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("id_pedido", idPedido);
        values.put("codigo_produto", codigoProduto);
        values.put("quantidade", quantidade);
        values.put("preco", preco);
        String dataAtual = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        values.put("data_pedido", dataAtual);

        long id = db.insert("pedidos", null, values);
        Log.d("DB_PEDIDOS", "Produto inserido no pedido -> id: " + id + ", id_pedido: " + idPedido + ", codigo: " + codigoProduto + ", quantidade: " + quantidade);
    }

    public int gerarIdPedido() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT MAX(id_pedido) AS max_id FROM pedidos", null);
        int novoId = 1;
        if (cursor.moveToFirst()) {
            novoId = cursor.getInt(cursor.getColumnIndexOrThrow("max_id")) + 1;
        }
        cursor.close();
        return novoId;
    }

    // Listar pedidos com informações do produto
    public JSONArray listarPedidos() {
        JSONArray array = new JSONArray();
        SQLiteDatabase db = this.getReadableDatabase();

        String sql = "SELECT p.id, p.codigo_produto, pr.descricao, " +
                "p.quantidade, p.preco, p.data_pedido " +
                "FROM pedidos p " +
                "INNER JOIN produtos_cartazista pr ON p.codigo_produto = pr.codigo " +
                "ORDER BY p.id DESC";

        Cursor cursor = db.rawQuery(sql, null);

        if (cursor.moveToFirst()) {
            do {
                try {
                    JSONObject obj = new JSONObject();
                    obj.put("id", cursor.getInt(cursor.getColumnIndexOrThrow("id")));
                    obj.put("codigo_produto", cursor.getInt(cursor.getColumnIndexOrThrow("codigo_produto")));
                    obj.put("descricao", cursor.getString(cursor.getColumnIndexOrThrow("descricao")));
                    obj.put("quantidade", cursor.getInt(cursor.getColumnIndexOrThrow("quantidade")));
                    obj.put("preco", cursor.getDouble(cursor.getColumnIndexOrThrow("preco")));
                    obj.put("data_pedido", cursor.getString(cursor.getColumnIndexOrThrow("data_pedido")));
                    array.put(obj);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } while (cursor.moveToNext());
        }
        cursor.close();
        return array;
    }

    // Converte o Cursor em uma List<Produto>
    public List<Produto> listarProdutosCartazistaComoLista() {
        List<Produto> lista = new ArrayList<>();
        Cursor cursor = listarProdutosCartazista(); // seu método que já existe
        if (cursor != null && cursor.moveToFirst()) {
            do {
                Produto p = new Produto();
                p.setCodigo(cursor.getInt(cursor.getColumnIndexOrThrow("codigo")));
                p.setDescricao(cursor.getString(cursor.getColumnIndexOrThrow("descricao")));
                p.setCategoria(cursor.getString(cursor.getColumnIndexOrThrow("categoria")));
                p.setPreco(cursor.getDouble(cursor.getColumnIndexOrThrow("preco")));
                p.setEmbalagem(cursor.getString(cursor.getColumnIndexOrThrow("embalagem")));
                p.setQtdPorEmbalagem(cursor.getDouble(cursor.getColumnIndexOrThrow("qtd_por_embalagem")));
                lista.add(p);
            } while (cursor.moveToNext());
            cursor.close();
        }
        return lista;
    }


    public void criarTabelaEstoque(SQLiteDatabase db) {
        String createTableEstoque = "CREATE TABLE IF NOT EXISTS estoque_atual (" +
                "codigo_produto INTEGER PRIMARY KEY, " +
                "quantidade REAL NOT NULL, " +
                "FOREIGN KEY (codigo_produto) REFERENCES produtos_cartazista(codigo)" +
                ")";
        db.execSQL(createTableEstoque);
    }

    public void inicializarEstoque(SQLiteDatabase db) {
        // Verifica se a tabela estoque_atual já tem dados
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM estoque_atual", null);
        cursor.moveToFirst();
        int count = cursor.getInt(0);
        cursor.close();

        if (count > 0) {
            Log.d("BancoDeDados", "Estoque já inicializado — nada a fazer.");
            return; // Sai sem recriar nada
        }

        Log.d("BancoDeDados", "Inicializando estoque pela primeira vez...");

        // Pega todos os produtos da tabela produtos_cartazista e cria o estoque inicial
        Cursor produtos = db.rawQuery("SELECT codigo FROM produtos_cartazista", null);
        if (produtos.moveToFirst()) {
            do {
                int codigo = produtos.getInt(0);
                db.execSQL("INSERT OR IGNORE INTO estoque_atual (codigo_produto, quantidade) VALUES (?, ?)",
                        new Object[]{codigo, 0});
            } while (produtos.moveToNext());
        }
        produtos.close();
    }


    public void atualizarEstoque(int codigoProduto, double novaQuantidade) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("quantidade", novaQuantidade);
        db.update("estoque_atual", values, "codigo_produto = ?", new String[]{String.valueOf(codigoProduto)});
    }

    // Listar estoque completo
    public JSONArray listarEstoque() {
        JSONArray array = new JSONArray();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT e.codigo_produto, p.descricao, p.embalagem, e.quantidade " +
                        "FROM estoque_atual e " +
                        "INNER JOIN produtos_cartazista p ON e.codigo_produto = p.codigo " +
                        "ORDER BY p.descricao ASC", null);

        Log.d("Bancodedados", "Cursor estoque possui " + cursor.getCount() + " registros"); // <- log

        if (cursor.moveToFirst()) {
            do {
                try {
                    JSONObject obj = new JSONObject();
                    obj.put("codigo_produto", cursor.getInt(cursor.getColumnIndexOrThrow("codigo_produto")));
                    obj.put("descricao", cursor.getString(cursor.getColumnIndexOrThrow("descricao")));
                    obj.put("embalagem", cursor.getString(cursor.getColumnIndexOrThrow("embalagem")));
                    obj.put("quantidade_atual", cursor.getDouble(cursor.getColumnIndexOrThrow("quantidade")));
                    obj.put("media_semanal", 0);
                    array.put(obj);
                    Log.d("Bancodedados", "Item do estoque: " + obj.toString()); // <- log de cada item
                } catch (Exception e) {
                    Log.e("Bancodedados", "Erro ao criar JSON do estoque", e);
                }
            } while (cursor.moveToNext());
        }
        cursor.close();
        return array;
    }

    public void atualizarEstoqueAposPedido(int codigoProduto, double quantidadeAdicional) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("UPDATE estoque_atual SET quantidade = quantidade + ? WHERE codigo_produto = ?",
                new Object[]{quantidadeAdicional, codigoProduto});
    }

    // Retorna a quantidade atual do estoque de um produto
    public double getQuantidadeEstoque(int codigoProduto) {
        SQLiteDatabase db = this.getReadableDatabase();
        double quantidade = 0;
        Cursor cursor = db.rawQuery(
                "SELECT quantidade FROM estoque_atual WHERE codigo_produto = ?",
                new String[]{String.valueOf(codigoProduto)}
        );

        if (cursor.moveToFirst()) {
            quantidade = cursor.getDouble(cursor.getColumnIndexOrThrow("quantidade"));
        }

        cursor.close();
        return quantidade;
    }



    private void criarTabelaUsoProdutos(SQLiteDatabase db) {
        String createTableUso = "CREATE TABLE IF NOT EXISTS uso_produtos (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "codigo_produto INTEGER NOT NULL, " +
                "quantidade_usada REAL NOT NULL, " +
                "data_registro TEXT DEFAULT (datetime('now','localtime')), " +
                "tipo TEXT DEFAULT 'uso', " + // 'uso' = uso diário, 'pedido_mes' = pedido do mês
                "FOREIGN KEY (codigo_produto) REFERENCES produtos_cartazista(codigo)" +
                ")";
        db.execSQL(createTableUso);
    }


    public void registrarUsoProduto(int codigoProduto, double quantidadeUsada, String tipo) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("codigo_produto", codigoProduto);
        values.put("quantidade_usada", quantidadeUsada);
        values.put("tipo", tipo);
        db.insert("uso_produtos", null, values);
    }


    public JSONArray getDadosUsoProdutos() {
        SQLiteDatabase db = this.getReadableDatabase();
        JSONArray jsonArray = new JSONArray();

        String query = "SELECT p.descricao, SUM(u.quantidade_usada) AS total_usado, u.tipo " +
                "FROM uso_produtos u " +
                "JOIN produtos_cartazista p ON u.codigo_produto = p.codigo " +
                "GROUP BY p.descricao, u.tipo " +
                "ORDER BY total_usado DESC";

        Cursor cursor = db.rawQuery(query, null);

        if (cursor.moveToFirst()) {
            do {
                try {
                    JSONObject obj = new JSONObject();
                    obj.put("nome", cursor.getString(0));
                    obj.put("total", cursor.getDouble(1));
                    obj.put("tipo", cursor.getString(2));
                    jsonArray.put(obj);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        return jsonArray;
    }



}


