# This is an adapted example from the Pytorch MNIST RNN: https://github.com/pytorch/examples/tree/main/mnist_rnn
import torch
import torch.nn as nn
import torch.nn.functional as F
import torch.optim as optim
import zipfile
from io import BytesIO
from torch.optim.lr_scheduler import StepLR
from torchvision import datasets, transforms

transform = None
model = None
classes = None


class Net(nn.Module):
    def __init__(self):
        super(Net, self).__init__()
        self.rnn = nn.LSTM(input_size=28, hidden_size=64, batch_first=True)
        self.batchnorm = nn.BatchNorm1d(64)
        self.dropout1 = nn.Dropout(0.25)
        self.dropout2 = nn.Dropout(0.5)
        self.fc1 = nn.Linear(64, 32)
        self.fc2 = nn.Linear(32, 10)

    def forward(self, input):
        # Shape of input is (batch_size,1, 28, 28)
        # converting shape of input to (batch_size, 28, 28)
        # as required by RNN when batch_first is set True
        input = input.reshape(-1, 28, 28)
        output, hidden = self.rnn(input)

        # RNN output shape is (seq_len, batch, input_size)
        # Get last output of RNN
        output = output[:, -1, :]
        output = self.batchnorm(output)
        output = self.dropout1(output)
        output = self.fc1(output)
        output = F.relu(output)
        output = self.dropout2(output)
        output = self.fc2(output)
        output = F.log_softmax(output, dim=1)
        return output


def train(model, device, train_loader, optimizer, epoch):
    model.train()
    for batch_idx, (data, target) in enumerate(train_loader):
        data, target = data.to(device), target.to(device)
        optimizer.zero_grad()
        output = model(data)
        loss = F.nll_loss(output, target)
        loss.backward()
        optimizer.step()
        print('Train Epoch: {} [{}/{} ({:.0f}%)]\tLoss: {:.6f}'.format(
            epoch, batch_idx * len(data), len(train_loader.dataset),
                   100. * batch_idx / len(train_loader), loss.item()))


def test(model, device, test_loader):
    model.eval()
    test_loss = 0
    correct = 0
    with torch.no_grad():
        for data, target in test_loader:
            data, target = data.to(device), target.to(device)
            output = model(data)
            test_loss += F.nll_loss(output, target, reduction='sum').item()  # sum up batch loss
            pred = output.argmax(dim=1, keepdim=True)  # get the index of the max log-probability
            correct += pred.eq(target.view_as(pred)).sum().item()

    test_loss /= len(test_loader.dataset)

    print('\nTest set: Average loss: {:.4f}, Accuracy: {}/{} ({:.0f}%)\n'.format(
        test_loss, correct, len(test_loader.dataset),
        100. * correct / len(test_loader.dataset)))
    return test_loss


def run_pytorch_example():
    epochs = 2
    seed = 1
    batch_size = 1000
    test_batch_size = 1000
    learning_rate = 0.2
    gamma = 0.7

    torch.manual_seed(seed)

    device = torch.device("cpu")

    train_loader = torch.utils.data.DataLoader(
        datasets.MNIST('/tmp/data', train=True,
                       transform=transforms.Compose([
                           transforms.ToTensor(),
                           transforms.Normalize((0.1307,), (0.3081,))
                       ])),
        batch_size=batch_size, shuffle=True)
    test_loader = torch.utils.data.DataLoader(
        datasets.MNIST('/tmp/data', train=False, transform=transforms.Compose([
            transforms.ToTensor(),
            transforms.Normalize((0.1307,), (0.3081,))
        ])),
        batch_size=test_batch_size, shuffle=True)

    model = Net().to(device)
    optimizer = optim.Adadelta(model.parameters(), lr=learning_rate)

    scheduler = StepLR(optimizer, step_size=1, gamma=gamma)
    last_loss = None

    for epoch in range(1, epochs + 1):
        train(model, device, train_loader, optimizer, epoch)
        last_loss = test(model, device, test_loader)
        scheduler.step()
    return last_loss


def on_enclave_startup():
    print("Enclave ready...")


def read_file_from_client(f, zip_destination_path):
    # Read the model from the Mail body
    model_size = int.from_bytes(f.read(4), byteorder='big')
    global model
    # Get the zip file from the client and saves it locally
    with open(zip_destination_path, "wb") as binary_file:
        binary_file.write(f.read(model_size))


def unzip_bundle_to_temporary_dir(zip_file):
    with zipfile.ZipFile(zip_file, 'r') as zip_ref:
        zip_ref.extractall("/tmp/")


def prepare_model_data(f):
    zip_destination_path = "bundle.zip"
    read_file_from_client(f, zip_destination_path)
    unzip_bundle_to_temporary_dir(zip_destination_path)


def receive_enclave_mail(mail):
    f = BytesIO(mail.body)

    prepare_model_data(f)
    return str.encode(str(run_pytorch_example()))
