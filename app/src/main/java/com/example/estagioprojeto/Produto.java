package com.example.estagioprojeto;

public class Produto {
    private int codigo;
    private String descricao;
    private String categoria;
    private double preco;
    private String embalagem;
    private double qtdPorEmbalagem;

    // Construtor completo
    public Produto(int codigo, String descricao, String categoria, double preco, String embalagem, double qtdPorEmbalagem) {
        this.codigo = codigo;
        this.descricao = descricao;
        this.categoria = categoria;
        this.preco = preco;
        this.embalagem = embalagem;
        this.qtdPorEmbalagem = qtdPorEmbalagem;
    }

    // Construtor vazio
    public Produto() { }

    // Getters e setters
    public int getCodigo() { return codigo; }
    public void setCodigo(int codigo) { this.codigo = codigo; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }

    public double getPreco() { return preco; }
    public void setPreco(double preco) { this.preco = preco; }

    public String getEmbalagem() { return embalagem; }
    public void setEmbalagem(String embalagem) { this.embalagem = embalagem; }

    public double getQtdPorEmbalagem() { return qtdPorEmbalagem; }
    public void setQtdPorEmbalagem(double qtdPorEmbalagem) { this.qtdPorEmbalagem = qtdPorEmbalagem; }

    @Override
    public String toString() {
        return "Produto{" +
                "codigo=" + codigo +
                ", descricao='" + descricao + '\'' +
                ", categoria='" + categoria + '\'' +
                ", preco=" + preco +
                ", embalagem='" + embalagem + '\'' +
                ", qtdPorEmbalagem=" + qtdPorEmbalagem +
                '}';
    }
}
