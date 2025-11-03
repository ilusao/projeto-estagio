package com.example.estagioprojeto;

import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.webkit.WebChromeClient;
import android.webkit.JavascriptInterface;
import android.database.sqlite.SQLiteDatabase;
import androidx.appcompat.app.AppCompatActivity;
import android.database.Cursor;
import android.util.Log;
import android.webkit.ConsoleMessage;
import java.util.List;
import org.json.JSONException;

import org.json.JSONArray;
import org.json.JSONObject;

public class ProdutosActivity extends AppCompatActivity {

    private WebView webView;
    private Bancodedados dbHelper;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_produtos);

        dbHelper = new Bancodedados(this);

        // Abre o banco e cria/inicializa a tabela de estoque
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        dbHelper.criarTabelaEstoque(db);   // cria a tabela se não existir
        dbHelper.inicializarEstoque(db);
        JSONArray dadosUso = dbHelper.getDadosUsoProdutos();
        String script = "javascript:atualizarGraficoUso(" + dadosUso.toString() + ")";

        webView = findViewById(R.id.webViewProdutos);
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);

        // Captura logs do JS no Logcat
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                Log.d("JS_LOG", consoleMessage.message() + " -- linha: " + consoleMessage.lineNumber());
                return true;
            }
        });

        // Gera um novo id_pedido para esta "sessão de pedidos"
        final int novoIdPedido = dbHelper.gerarIdPedido();

        // Interface para o JS acessar os produtos
        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public String getProdutosCartazista() {
                List<Produto> produtos = dbHelper.listarProdutosCartazistaComoLista();
                JSONArray jsonArray = new JSONArray();

                for (Produto p : produtos) {
                    try {
                        JSONObject obj = new JSONObject();
                        obj.put("codigo", p.getCodigo());
                        obj.put("descricao", p.getDescricao());
                        obj.put("categoria", p.getCategoria());
                        obj.put("preco", p.getPreco());
                        obj.put("embalagem", p.getEmbalagem());
                        obj.put("qtd_por_embalagem", p.getQtdPorEmbalagem());
                        jsonArray.put(obj);
                    } catch (Exception e) {
                        Log.e("ProdutosActivity", "Erro ao criar JSON do produto", e);
                    }
                }

                Log.d("ProdutosActivity", "JSON final de produtos: " + jsonArray.toString());
                return jsonArray.toString();
            }

            @JavascriptInterface
            public void finalizarPedido(int codigo, int quantidade, double preco) {
                dbHelper.inserirPedido(novoIdPedido, codigo, quantidade, preco);
                dbHelper.atualizarEstoqueAposPedido(codigo, quantidade);
                Log.d("ProdutosActivity", "Pedido inserido e estoque atualizado: " + codigo + " x" + quantidade + " (id_pedido: " + novoIdPedido + ")");
            }

            @JavascriptInterface
            public String getDadosUsoProdutos() {
                JSONArray jsonArray = new JSONArray();
                SQLiteDatabase db = dbHelper.getReadableDatabase();
                Log.d("DEBUG_GRAFICO", "Entrou em getDadosUsoProdutos()");
                Cursor cursor = db.rawQuery("SELECT codigo, descricao FROM produtos_cartazista", null);
                Log.d("DEBUG_GRAFICO", "Total de produtos: " + cursor.getCount());

                if (cursor.moveToFirst()) {
                    do {
                        int codigo = cursor.getInt(0);
                        String nome = cursor.getString(1);

                        // Uso diário
                        double totalUso = 0;
                        Cursor cUso = db.rawQuery("SELECT SUM(quantidade_usada) FROM uso_produtos WHERE codigo_produto=? AND tipo='uso'",
                                new String[]{String.valueOf(codigo)});
                        if (cUso.moveToFirst()) totalUso = cUso.isNull(0) ? 0 : cUso.getDouble(0);
                        cUso.close();

// Pedido do mês
                        double totalPedido = 0;
                        Cursor cPedido = db.rawQuery("SELECT SUM(quantidade_usada) FROM uso_produtos WHERE codigo_produto=? AND tipo='pedido_mes'",
                                new String[]{String.valueOf(codigo)});
                        if (cPedido.moveToFirst()) totalPedido = cPedido.isNull(0) ? 0 : cPedido.getDouble(0);
                        cPedido.close();

                        // Adiciona uso
                        try {
                            JSONObject objUso = new JSONObject();
                            objUso.put("nome", nome);
                            objUso.put("total", totalUso);
                            objUso.put("tipo", "uso");
                            jsonArray.put(objUso);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }

                        // Adiciona pedido do mês
                        try {
                            JSONObject objPedido = new JSONObject();
                            objPedido.put("nome", nome);
                            objPedido.put("total", totalPedido);
                            objPedido.put("tipo", "pedido_mes");
                            jsonArray.put(objPedido);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }

                    } while (cursor.moveToNext());
                }
                cursor.close();
                db.close();

                return jsonArray.toString();

            }



            @JavascriptInterface
            public String getPedidosDaSemana() {
                return ProdutosActivity.this.getPedidosDaSemana();
            }

            @JavascriptInterface
            public String getEstoque() {
                try {
                    JSONArray estoque = dbHelper.listarEstoque();
                    Log.d("ProdutosActivity", "JSON do estoque: " + estoque.toString()); // <- log
                    return estoque.toString();
                } catch (Exception e) {
                    Log.e("ProdutosActivity", "Erro ao listar estoque", e);
                    return "[]";
                }
            }



            @JavascriptInterface
            public void setEstoque(int codigoProduto, double novaQuantidade) {
                try {
                    double estoqueAnterior = dbHelper.getQuantidadeEstoque(codigoProduto);
                    dbHelper.atualizarEstoque(codigoProduto, novaQuantidade);

                    double quantidadeUsada = estoqueAnterior - novaQuantidade;

                    if (quantidadeUsada > 0) {
                        // 🔹 Registrar uso já com tipo 'uso'
                        dbHelper.registrarUsoProduto(codigoProduto, quantidadeUsada, "uso");

                        Log.d("ProdutosActivity", "Uso registrado: produto=" + codigoProduto + ", qtd=" + quantidadeUsada);

                        // 🔹 Atualiza gráfico imediatamente, garantindo pegar todos os dados
                        runOnUiThread(() -> {
                            JSONArray dadosUso = new JSONArray();
                            try {
                                dadosUso = new JSONArray(dbHelper.getDadosUsoProdutos());
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                            final String script = "javascript:atualizarGraficoUso(" + dadosUso.toString() + ")";
                            webView.evaluateJavascript(script, null);
                        });
                    } else {
                        Log.d("ProdutosActivity", "Nenhum uso registrado (estoque aumentou ou manteve igual)");
                    }

                } catch (Exception e) {
                    Log.e("ProdutosActivity", "Erro ao atualizar estoque e registrar uso", e);
                }
            }



            public double getEstoqueAtual(int codigoProduto) {
                SQLiteDatabase db = dbHelper.getReadableDatabase();
                double quantidade = 0;

                Cursor cursor = db.rawQuery("SELECT quantidade FROM estoque_atual WHERE codigo_produto = ?", new String[]{String.valueOf(codigoProduto)});
                if (cursor.moveToFirst()) {
                    quantidade = cursor.getDouble(0);
                }
                cursor.close();
                return quantidade;
            }

            @JavascriptInterface
            public String getPedidosDoMes() {
                return ProdutosActivity.this.getPedidosDoMes();
            }



        }, "Android");

        webView.loadUrl("file:///android_asset/produtos.html");
    }



    @JavascriptInterface
    public String getPedidosDaSemana() {
        JSONArray array = new JSONArray();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        try {
            String sql = "SELECT p.id, p.id_pedido, p.codigo_produto, pr.descricao, pr.embalagem, pr.qtd_por_embalagem, " +
                    "p.quantidade, p.preco, p.data_pedido " +
                    "FROM pedidos p " +
                    "INNER JOIN produtos_cartazista pr ON p.codigo_produto = pr.codigo " +
                    "WHERE date(p.data_pedido) >= date('now','-6 days') " +
                    "ORDER BY p.data_pedido ASC";

            Cursor cursor = db.rawQuery(sql, null);

            if(cursor.moveToFirst()) {
                do {
                    try {
                        JSONObject obj = new JSONObject();
                        obj.put("id", cursor.getInt(cursor.getColumnIndexOrThrow("id")));
                        obj.put("id_pedido", cursor.getInt(cursor.getColumnIndexOrThrow("id_pedido"))); // <-- adicionado
                        obj.put("codigo_produto", cursor.getInt(cursor.getColumnIndexOrThrow("codigo_produto")));
                        obj.put("descricao", cursor.getString(cursor.getColumnIndexOrThrow("descricao")));
                        obj.put("embalagem", cursor.getString(cursor.getColumnIndexOrThrow("embalagem")));
                        obj.put("qtd_por_embalagem", cursor.getDouble(cursor.getColumnIndexOrThrow("qtd_por_embalagem")));
                        obj.put("quantidade", cursor.getInt(cursor.getColumnIndexOrThrow("quantidade")));
                        obj.put("preco", cursor.getDouble(cursor.getColumnIndexOrThrow("preco")));
                        obj.put("data_pedido", cursor.getString(cursor.getColumnIndexOrThrow("data_pedido")));
                        array.put(obj);
                    } catch (Exception e) {
                        Log.e("getPedidosDaSemana", "Erro ao processar registro do cursor", e);
                    }
                } while(cursor.moveToNext());
            }
            if (cursor != null) cursor.close();
        } catch (Exception e) {
            Log.e("getPedidosDaSemana", "Erro na query SQL", e);
        }

        return array.toString();
    }


    @JavascriptInterface
    public String getPedidosDoMes() {
        JSONArray array = new JSONArray();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String sql = "SELECT strftime('%W', p.data_pedido) AS semana, pr.descricao, " +
                "SUM(p.quantidade) AS quantidade_total, SUM(p.preco * p.quantidade) AS valor_total " +
                "FROM pedidos p " +
                "INNER JOIN produtos_cartazista pr ON p.codigo_produto = pr.codigo " +
                "WHERE strftime('%m', p.data_pedido) = strftime('%m','now') " +
                "GROUP BY semana, pr.descricao " +
                "ORDER BY semana ASC";

        Cursor cursor = db.rawQuery(sql, null);

        if(cursor.moveToFirst()) {
            do {
                try {
                    JSONObject obj = new JSONObject();
                    obj.put("semana", cursor.getString(cursor.getColumnIndexOrThrow("semana")));
                    obj.put("descricao", cursor.getString(cursor.getColumnIndexOrThrow("descricao")));
                    obj.put("quantidade_total", cursor.getInt(cursor.getColumnIndexOrThrow("quantidade_total")));
                    obj.put("valor_total", cursor.getDouble(cursor.getColumnIndexOrThrow("valor_total")));
                    array.put(obj);
                } catch (Exception e) { e.printStackTrace(); }
            } while(cursor.moveToNext());
        }

        cursor.close();
        return array.toString();
    }



}
