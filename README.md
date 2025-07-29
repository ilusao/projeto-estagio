# 📱 Sistema de Controle de Faixas e Cartazes para Cartazista

Este projeto é um aplicativo Android voltado para **cartazistas**, com o objetivo de agilizar o trabalho de controle de faixas e cartazes, além de ajudar no controle de estoque de produtos usados pelo cartazista.

---

## 🎯 **Objetivo do sistema**
✅ Diminuir o tempo gasto na procura de faixas  
✅ Informar facilmente quais faixas estão disponíveis  
✅ Facilitar o controle de estoque dos materiais usados pelo cartazista  
✅ Rodar **offline**, para funcionar mesmo sem internet

---

## ⚙️ **Como o app funciona**
- A interface foi criada com:
  - **HTML**
  - **CSS** (incluindo o framework Bootstrap, baixado localmente para funcionar offline)
  - **JavaScript** (para interações e navegação entre telas)

- O aplicativo é exibido dentro de um **WebView** no Android, que permite rodar o HTML/CSS/JavaScript como se fosse uma página da web, mas embutida no app.

- Atualmente não há banco de dados implementado, mas está previsto integrar futuramente com **SQL** ou **MySQL** (possivelmente usando MySQLAdmin) para gerenciar dados de estoque e faixas.

---

## 🧰 **Softwares e tecnologias usados**
- **Bootstrap** (pasta local dentro do projeto, para garantir funcionamento offline)
- **HTML, CSS, JavaScript**
- **WebView** (recurso do Android para exibir conteúdos web no app)
- **Android Studio** (ambiente de desenvolvimento)

---

## ✏ **Status do projeto**
✅ Estrutura de telas criada  
✅ Estilo responsivo com Bootstrap  
⚙️ Em desenvolvimento: funcionalidades de controle de estoque e integração com banco de dados

---

## 🚀 **Próximos passos**
- Implementar banco de dados (SQL ou MySQL)
- adicionar varias telas como cadastro de faixas e tela de contagem de produtos do cartazista que estão no estoque (no caso picel, cartazes, madeira para faixa etc) entre outras telas
- Melhorar design e usabilidade
- Testar em diferentes tamanhos de dispositivos Android, futuramente no pc

---

> 📌 **Observação:** todo o código HTML/CSS/JS fica na pasta `assets/` do projeto Android para garantir que o app funcione mesmo sem conexão com a internet.

---

## ✒ **Autor**
- andré
