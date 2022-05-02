let fact x =
    if x <= 1 then 1
    else x * fact (x - 1)

type nat = Zero | Succ of nat

let zero = Zero
and one = Succ(Zero)
and two = Succ(Succ(Zero))
let three = Succ(two)
let four = Succ(three)