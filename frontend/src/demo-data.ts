export type DemoArticle = {
  code: string
  name: string
  category: string
  stock: number
  unit: string
  cost: number
  price: number
  supplier: string
}

export type DemoCustomer = {
  code: string
  name: string
  phone: string
  email: string
  currentAccount: number
  creditLimit: number
  status: 'Activo' | 'Inactivo'
}

export type DemoSupplier = {
  code: string
  name: string
  specialty: string
  phone: string
  email: string
  balance: number
  status: 'Activo' | 'Inactivo'
}

const articleFamilies: { category: string; product: string; variants: string[]; baseCost: number }[] = [
  { category: 'Fijaciones', product: 'Tornillo para madera zincado', variants: ['3 × 20 mm', '3 × 30 mm', '4 × 40 mm', '5 × 50 mm'], baseCost: 90 },
  { category: 'Fijaciones', product: 'Tornillo autoperforante', variants: ['8 × 1/2 pulg.', '8 × 3/4 pulg.', '10 × 1 pulg.', '12 × 1 1/2 pulg.'], baseCost: 120 },
  { category: 'Fijaciones', product: 'Taco de nylon', variants: ['N.º 5', 'N.º 6', 'N.º 8', 'N.º 10'], baseCost: 75 },
  { category: 'Fijaciones', product: 'Clavo punta París', variants: ['1 pulg.', '1 1/2 pulg.', '2 pulg.', '3 pulg.'], baseCost: 55 },
  { category: 'Herramientas manuales', product: 'Destornillador plano', variants: ['3 × 75 mm', '5 × 100 mm', '6 × 150 mm', '8 × 200 mm'], baseCost: 2400 },
  { category: 'Herramientas manuales', product: 'Destornillador Phillips', variants: ['PH0 × 75 mm', 'PH1 × 100 mm', 'PH2 × 150 mm', 'PH3 × 200 mm'], baseCost: 2600 },
  { category: 'Herramientas manuales', product: 'Llave combinada', variants: ['8 mm', '10 mm', '13 mm', '17 mm'], baseCost: 3200 },
  { category: 'Herramientas manuales', product: 'Pinza universal', variants: ['6 pulg.', '7 pulg.', '8 pulg.', '10 pulg.'], baseCost: 6800 },
  { category: 'Herramientas manuales', product: 'Martillo de carpintero', variants: ['300 g', '400 g', '500 g', '600 g'], baseCost: 7500 },
  { category: 'Herramientas manuales', product: 'Cinta métrica', variants: ['3 m', '5 m', '8 m', '10 m'], baseCost: 2800 },
  { category: 'Herramientas eléctricas', product: 'Mecha para pared', variants: ['5 mm', '6 mm', '8 mm', '10 mm'], baseCost: 950 },
  { category: 'Herramientas eléctricas', product: 'Disco de corte', variants: ['115 mm fino', '115 mm grueso', '180 mm fino', '230 mm fino'], baseCost: 1100 },
  { category: 'Electricidad', product: 'Cable unipolar', variants: ['1,5 mm² × 10 m', '2,5 mm² × 10 m', '4 mm² × 10 m', '6 mm² × 10 m'], baseCost: 8400 },
  { category: 'Electricidad', product: 'Tomacorriente doble', variants: ['Blanco 10 A', 'Blanco 20 A', 'Marfil 10 A', 'Negro 10 A'], baseCost: 4100 },
  { category: 'Electricidad', product: 'Cinta aisladora', variants: ['Negra 10 m', 'Negra 20 m', 'Azul 10 m', 'Roja 10 m'], baseCost: 900 },
  { category: 'Plomería', product: 'Codo de PVC 90°', variants: ['20 mm', '25 mm', '32 mm', '40 mm'], baseCost: 420 },
  { category: 'Plomería', product: 'Niple galvanizado', variants: ['1/2 × 50 mm', '1/2 × 100 mm', '3/4 × 50 mm', '3/4 × 100 mm'], baseCost: 1350 },
  { category: 'Plomería', product: 'Cinta de teflón', variants: ['12 mm × 10 m', '12 mm × 20 m', '19 mm × 10 m', '19 mm × 20 m'], baseCost: 650 },
  { category: 'Pinturería', product: 'Pincel', variants: ['1 pulg.', '1 1/2 pulg.', '2 pulg.', '3 pulg.'], baseCost: 1600 },
  { category: 'Pinturería', product: 'Rodillo de lana', variants: ['10 cm', '17 cm', '22 cm', '25 cm'], baseCost: 2400 },
  { category: 'Pinturería', product: 'Lija al agua', variants: ['N.º 100', 'N.º 180', 'N.º 240', 'N.º 400'], baseCost: 300 },
  { category: 'Adhesivos y selladores', product: 'Silicona neutra', variants: ['Transparente 280 ml', 'Blanca 280 ml', 'Negra 280 ml', 'Gris 280 ml'], baseCost: 3900 },
  { category: 'Adhesivos y selladores', product: 'Adhesivo de contacto', variants: ['125 ml', '250 ml', '500 ml', '1 litro'], baseCost: 2500 },
  { category: 'Seguridad', product: 'Guante moteado', variants: ['Talle 8', 'Talle 9', 'Talle 10', 'Talle 11'], baseCost: 950 },
  { category: 'Seguridad', product: 'Antiparra transparente', variants: ['Clásica', 'Lente antiempaño', 'Con ventilación', 'Panorámica'], baseCost: 3200 },
]

export const demoArticles: DemoArticle[] = articleFamilies.flatMap((family, familyIndex) =>
  family.variants.map((variant, variantIndex) => {
    const sequence = familyIndex * 4 + variantIndex + 1
    const cost = family.baseCost + variantIndex * Math.max(25, Math.round(family.baseCost * 0.08))
    return {
      code: `FER-${String(sequence).padStart(4, '0')}`,
      name: `${family.product} ${variant}`,
      category: family.category,
      stock: 8 + ((sequence * 17) % 93),
      unit: family.category === 'Fijaciones' ? 'unidad' : 'unidad',
      cost,
      price: Math.round(cost * 1.42 / 10) * 10,
      supplier: `PROV-${String(((sequence - 1) % 10) + 1).padStart(2, '0')}`,
    }
  }),
)

export const demoCustomers: DemoCustomer[] = Array.from({ length: 20 }, (_, index) => {
  const number = index + 1
  return {
    code: `CLI-${String(number).padStart(3, '0')}`,
    name: `Cliente de Prueba ${String(number).padStart(2, '0')}`,
    phone: `+54 11 5550-${String(1000 + number)}`,
    email: `cliente${String(number).padStart(2, '0')}@example.test`,
    currentAccount: number % 4 === 0 ? number * 1250 : 0,
    creditLimit: 50000 + (number % 5) * 25000,
    status: number === 20 ? 'Inactivo' : 'Activo',
  }
})

const specialties = ['Tornillería', 'Herramientas', 'Electricidad', 'Plomería', 'Pinturería', 'Seguridad', 'Adhesivos', 'Materiales', 'Ferretería general', 'Fijaciones']

export const demoSuppliers: DemoSupplier[] = Array.from({ length: 20 }, (_, index) => {
  const number = index + 1
  return {
    code: `PROV-${String(number).padStart(2, '0')}`,
    name: `Proveedor de Prueba ${String(number).padStart(2, '0')}`,
    specialty: specialties[index % specialties.length],
    phone: `+54 11 5551-${String(1000 + number)}`,
    email: `proveedor${String(number).padStart(2, '0')}@example.test`,
    balance: number % 3 === 0 ? number * 9750 : 0,
    status: number === 20 ? 'Inactivo' : 'Activo',
  }
})
