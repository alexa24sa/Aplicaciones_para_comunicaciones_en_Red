class AFD:
    def __init__(self):
        # Estados
        self.Q = {"A", "B", "C", "D"}  # D es el estado de rechazo
        # Alfabeto
        self.sigma = {"0", "1"}
        # Estado inicial
        self.q0 = "A"
        # Estados finales
        self.F = {"A", "B", "C"}
        # Función de transición
        self.delta = {
            "A": {"0": "B", "1": "C"},
            "B": {"0": "B", "1": "D"},
            "C": {"0": "D", "1": "D"},
            "D": {"0": "D", "1": "D"}  # Estado trampa
        }
    
    def process(self, cadena):
        estado_actual = self.q0
        
        for simbolo in cadena:
            if simbolo not in self.sigma:
                return f"Cadena inválida: contiene '{simbolo}', que no está en el alfabeto {self.sigma}"
            estado_actual = self.delta[estado_actual].get(simbolo, "D")
        
        return "Cadena ACEPTADA" if estado_actual in self.F else "Cadena RECHAZADA"

if __name__ == "__main__":
    automata = AFD()

    # Pruebas con diferentes cadenas
    test_cadenas = ["", "0", "00", "1", "01", "10", "111", "0001", "0010"]

    for cadena in test_cadenas:
        resultado = automata.process(cadena)
        print(f"Entrada: '{cadena}' → {resultado}")
